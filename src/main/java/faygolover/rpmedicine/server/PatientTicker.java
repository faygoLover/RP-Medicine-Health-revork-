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
    public static void tickPlayer(ServerPlayer sp) {
        MedicalData d = Medical.data(sp);
        if (d == null) return;
        MedicalSettings s = MedicalSettings.get();
        MedicalState m = d.state;

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
        if (t % step != 0) return;

        // На койке здоровый тоже «спит»: множители койки нужны только тому, кто лечится.
        boolean quiet = m.isQuiet(s) && d.distance < 0.01;
        if (!quiet) {
            long t0 = System.nanoTime();
            StepInput in = new StepInput(step / 20.0);
            in.traits = Medical.traits(sp);
            in.online = true;
            in.sprintSeconds = d.sprintTicks / 20.0;
            in.jumps = d.jumps;
            in.distance = d.distance;
            in.suffocating = sp.getAirSupply() <= 0 && (sp.isEyeInFluid(FluidTags.WATER) || sp.isInWall());
            in.still = d.distance < 0.05 * step || m.isDown();
            in.random = RANDOM.split();
            HospitalService.applyConditions(d, in, s);
            StepResult r = Physiology.step(m, in, s);
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
