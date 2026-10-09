package faygolover.rpmedicine.server;

import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.GameplayEffects;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.core.StepInput;
import faygolover.rpmedicine.core.StepResult;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.hospital.HospitalService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;

import java.util.SplittableRandom;

/**
 * Пересчёт физиологии раз в {@code stepTicks} тиков. Игроки распределены по тикам (сдвиг по id),
 * чтобы нагрузка не собиралась в один тик. У здоровых («спящий режим») физиология не считается.
 */
public final class PatientTicker {
    private PatientTicker() {}

    private static final SplittableRandom RANDOM = new SplittableRandom();

    /** Каждый тик игрока на сервере. */
    /** Маска невидимых конечностей: часть отсутствует и протеза нет. */
    public static int limbMask(faygolover.rpmedicine.core.MedicalState m) {
        int mask = 0;
        for (var ps : m.parts) if (ps.missing && ps.prosthesis == faygolover.rpmedicine.core.BodyPartState.Prosthesis.NONE) mask |= 1 << ps.part.ordinal();
        return mask;
    }

    /** Разослать видимость конечностей, если изменилась. */
    static void syncLimbs(ServerPlayer sp, faygolover.rpmedicine.capability.MedicalData d) {
        int mask = limbMask(d.state);
        if (mask == d.sentLimbMask) return;
        d.sentLimbMask = mask;
        faygolover.rpmedicine.network.Network.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                new faygolover.rpmedicine.network.LimbsVisualPacket(sp.getId(), mask));
    }

    public static void tickPlayer(ServerPlayer sp) {
        MedicalData d = Medical.data(sp);
        if (d == null) return;
        MedicalSettings s = MedicalSettings.get();
        MedicalState m = d.state;
        if (sp.tickCount % 20 == 0) syncLimbs(sp, d);

        // Ванильное здоровье всегда полное (кроме /kill и пустоты, которые убивают по-настоящему).
        if (!sp.isDeadOrDying() && sp.getHealth() < sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());

        // Накопители движения.
        if (sp.isSprinting()) d.sprintTicks++;
        if (!Double.isNaN(d.lastX) && sp.onGround()) {
            double dx = sp.getX() - d.lastX;
            double dz = sp.getZ() - d.lastZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 2) d.distance += dist;
        }
        d.lastX = sp.getX();
        d.lastZ = sp.getZ();

        int step = Math.max(1, s.stepTicks);
        int t = ++d.ticks + sp.getId();
        // История состояния для ГМа: снимок раз в 10 секунд (второй этап, п. 11.1).
        if (t % faygolover.rpmedicine.stats.History.PERIOD_TICKS == 0) faygolover.rpmedicine.stats.History.record(sp.getUUID(), m);
        if (t % 600 == 0) SurgeryService.tickMask(sp);
        if (t % step != 0) return;

        // На койке здоровый тоже «спит»: множители койки нужны только тому, кто лечится.
        StepInput in = new StepInput(step / 20.0);
        in.traits = Medical.traits(sp);
        in.online = true;
        in.sprintSeconds = d.sprintTicks / 20.0;
        in.jumps = d.jumps;
        in.distance = d.distance;
        in.suffocating = sp.getAirSupply() <= 0 && (sp.isEyeInFluid(FluidTags.WATER) || sp.isInWall());
        in.still = d.distance < 0.05 * step || m.isDown();
        in.withering = sp.hasEffect(net.minecraft.world.effect.MobEffects.WITHER);
        HospitalService.applyConditions(d, in, s);
        // Голод, жажда, среда (второй этап, п. 12): своя жажда убывает и у здорового.
        SurvivalService.prepareStep(sp, d, in, in.sprintSeconds);
        VanillaEffects.prepareStep(sp, m, in.dt);
        SubstanceService.tick(sp, m);
        boolean quiet = m.isQuiet(s) && d.distance < 0.01 && in.ambientTempShift == 0;
        if (!quiet) {
            long t0 = System.nanoTime();
            in.random = RANDOM.split();
            double dripBefore = m.salineDripRemaining + m.bloodDripRemaining;
            StepResult r = Physiology.step(m, in, s);
            // Влитое капельницей поит (RP Culinary).
            double infused = dripBefore - (m.salineDripRemaining + m.bloodDripRemaining);
            if (infused > 0) faygolover.rpmedicine.integration.IvNutrition.water(sp, infused);
            // Кашель курильщика — примерно раз в час в сети.
            if (in.traits.smoker && in.random.nextDouble() < s.smokerCoughsPerHour * in.dt / 3600.0)
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), faygolover.rpmedicine.registry.ModSounds.COUGH.get(),
                        net.minecraft.sounds.SoundSource.VOICE, 0.8f, faygolover.rpmedicine.medcard.MedcardService.voicePitch(sp));
            DownedService.onStep(sp, m, r);
            d.markDirty();
            Profiler.record(System.nanoTime() - t0);
        }
        d.sprintTicks = 0;
        d.jumps = 0;
        d.distance = 0;

        if (d.dirty) {
            d.dirty = false;
            GameplayEffects.Mods mods = GameplayEffects.compute(m, Medical.traits(sp), s);
            EffectsApplier.apply(sp, d, m, mods);
            SelfSync.sync(sp, d, m, mods);
        }
    }

    /** Каждый тик заглушки на сервере: физиология без заживления и восстановления. */
    public static void tickStub(BodyStubEntity stub) {
        MedicalSettings s = MedicalSettings.get();
        if ((stub.tickCount + stub.getId()) % faygolover.rpmedicine.stats.History.PERIOD_TICKS == 0)
            faygolover.rpmedicine.stats.History.record(stub.ownerId(), stub.state());
        int step = Math.max(1, s.stepTicks);
        if ((stub.tickCount + stub.getId()) % step != 0) return;
        MedicalState m = stub.state();
        if (m.isQuiet(s)) return;
        long t0 = System.nanoTime();
        StepInput in = new StepInput(step / 20.0);
        in.traits = stub.traits();
        in.online = false;
        in.still = true;
        in.random = RANDOM.split();
        // Заглушка на койке: заживление у неё не идёт и так (online = false), множители не нужны.
        StepResult r = Physiology.step(m, in, s);
        stub.markChanged();
        if (r.has(StepResult.Event.DIED)) StubService.onStubBrainDeath(stub);
        Profiler.record(System.nanoTime() - t0);
    }
}
