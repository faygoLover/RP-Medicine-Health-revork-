package faygolover.rpmedicine.gametest;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.StepInput;
import faygolover.rpmedicine.entity.BodyStubEntity;
import faygolover.rpmedicine.hospital.HospitalBlocks;
import faygolover.rpmedicine.hospital.HospitalFunction;
import faygolover.rpmedicine.hospital.HospitalService;
import faygolover.rpmedicine.server.CarryService;
import faygolover.rpmedicine.server.Medical;
import faygolover.rpmedicine.server.StubService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

import static faygolover.rpmedicine.gametest.MedicalGameTests.T;
import static faygolover.rpmedicine.gametest.MedicalGameTests.noDeath;
import static faygolover.rpmedicine.gametest.MedicalGameTests.player;
import static faygolover.rpmedicine.gametest.MedicalGameTests.remove;
import static faygolover.rpmedicine.gametest.MedicalGameTests.state;
import static faygolover.rpmedicine.gametest.MedicalGameTests.tickPatients;

/**
 * Сценарии второго этапа «Госпиталь» (п. 14 ТЗ второго этапа). Чужих модов с мебелью на тестовом
 * сервере нет, поэтому функции госпиталя назначаются ванильным блокам.
 */
@GameTestHolder(RpMedicine.MODID)
@PrefixGameTestTemplate(false)
public final class HospitalGameTests {
    private HospitalGameTests() {}

    /** Койка — белая шерсть, монитор — наблюдатель, стойка капельницы — железная решётка. */
    static void testBlocks() {
        HospitalBlocks.set(Map.of(
                HospitalFunction.BED, List.of("minecraft:white_wool"),
                HospitalFunction.MONITOR, List.of("minecraft:observer"),
                HospitalFunction.IV_STAND, List.of("minecraft:iron_bars")), Map.of());
    }

    private static MedicalData data(ServerPlayer p) {
        return Medical.data(p);
    }

    /** Лечь на койку, поза и хитбокс, ускорение, монитор, встать приседанием. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void bedLieDownMonitorStandUp(GameTestHelper h) {
        noDeath(false);
        testBlocks();
        BlockPos bed = h.absolutePos(new BlockPos(2, 1, 2));
        BlockPos monitor = h.absolutePos(new BlockPos(4, 1, 2));
        h.getLevel().setBlockAndUpdate(bed, Blocks.WHITE_WOOL.defaultBlockState());
        h.getLevel().setBlockAndUpdate(monitor, Blocks.OBSERVER.defaultBlockState());
        ServerPlayer p = player(h, 3.5, 2.5);
        h.assertTrue(HospitalService.onUseBlock(p, bed), "ПКМ по койке пустой рукой — лечь");
        h.assertTrue(bed.equals(data(p).bedPos), "игрок на койке");
        h.assertTrue(Math.abs(p.getY() - (bed.getY() + 1.0)) < 0.01, "лежит на верхней грани койки, y=" + p.getY());
        for (int i = 0; i < MedicalSettings.get().stepTicks; i++) tickPatients(p);
        h.assertTrue(p.getForcedPose() == Pose.SLEEPING, "поза на спине (сна), было " + p.getForcedPose());
        p.setPose(Pose.SLEEPING);
        h.assertTrue(Math.abs(p.getBbHeight() - 0.6f) < 0.01 && Math.abs(p.getBbWidth() - 0.6f) < 0.01,
                "хитбокс лежачего на койке 0,6, был " + p.getBbWidth() + "×" + p.getBbHeight());

        StepInput in = new StepInput(0.5);
        HospitalService.applyConditions(data(p), in, MedicalSettings.get());
        h.assertTrue(in.healFactor == MedicalSettings.get().bedHealFactor && in.brainRecoveryFactor == MedicalSettings.get().bedBrainRecoveryFactor,
                "койка ускоряет заживление и мозг");

        HospitalService.tickPlayer(p, data(p));
        h.assertTrue(monitor.equals(data(p).monitorPos), "монитор рядом с койкой найден");
        h.assertTrue(HospitalService.monitorPatient(h.getLevel(), monitor) == p, "монитор показывает этого пациента");

        // Вторым на занятую койку не лечь.
        ServerPlayer other = player(h, 1.5, 2.5);
        HospitalService.onUseBlock(other, bed);
        h.assertTrue(data(other).bedPos == null, "занятая койка");

        p.setShiftKeyDown(true);
        HospitalService.tickPlayer(p, data(p));
        h.assertTrue(data(p).bedPos == null, "присел — встал");
        h.assertTrue(p.blockPosition().distManhattan(bed) <= 2 && !p.blockPosition().equals(bed), "стоит рядом с койкой");
        remove(h, p, other);
        h.succeed();
    }

    /** Несущий кладёт лежачего на койку; поднятый с койки — уже не на ней. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void carryDownedOntoBed(GameTestHelper h) {
        noDeath(false);
        testBlocks();
        BlockPos bed = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(bed, Blocks.WHITE_WOOL.defaultBlockState());
        ServerPlayer medic = player(h, 3.5, 3.5);
        ServerPlayer patient = player(h, 3.5, 4.5);
        state(patient).down = MedicalState.Down.FAINT;
        state(patient).painShock = true;
        h.assertTrue(CarryService.pickUp(medic, patient), "поднял");
        h.assertTrue(HospitalService.onUseBlock(medic, bed), "ПКМ по койке с телом");
        h.assertTrue(bed.equals(data(patient).bedPos), "пациент на койке");
        h.assertTrue(!patient.isPassenger() && !CarryService.isCarrying(medic), "несущий больше не несёт");
        // Подняли снова — уже не на койке.
        h.assertTrue(CarryService.pickUp(medic, patient), "поднял с койки");
        HospitalService.tickPlayer(patient, data(patient));
        h.assertTrue(data(patient).bedPos == null, "унесли с койки");
        CarryService.dropCarried(medic);
        remove(h, medic, patient);
        h.succeed();
    }

    /** Выход на койке в сознании оставляет заглушку на койке; при входе игрок снова на ней. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void logoutOnBedLeavesStubOnBed(GameTestHelper h) {
        noDeath(false);
        testBlocks();
        BlockPos bed = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(bed, Blocks.WHITE_WOOL.defaultBlockState());
        ServerPlayer p = player(h, 3.5, 2.5);
        HospitalService.onUseBlock(p, bed);
        StubService.onLogout(p);
        List<BodyStubEntity> stubs = h.getLevel().getEntitiesOfClass(BodyStubEntity.class, new net.minecraft.world.phys.AABB(bed).inflate(2));
        h.assertTrue(stubs.size() == 1, "заглушка осталась, было " + stubs.size());
        BodyStubEntity stub = stubs.get(0);
        h.assertTrue(bed.equals(stub.bedPos()), "заглушка на койке");
        h.assertTrue(HospitalService.isOnBed(stub), "тело числится на койке (его покажет монитор)");
        h.runAfterDelay(3, () -> {
            StubService.onLogin(p);
            h.assertTrue(stub.isRemoved(), "заглушка исчезла");
            h.assertTrue(bed.equals(data(p).bedPos), "игрок снова на койке");
            remove(h, p);
            h.succeed();
        });
    }

    /** Забор крови у донора: пакет подписан его группой; переливание; порча вне холодильника. */
    @GameTest(template = T, timeoutTicks = 2400)
    public static void bloodDonationAndTransfusion(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        ServerPlayer medic = player(h, 3.5, 3.5);
        ServerPlayer donor = player(h, 2.5, 3.5);
        ServerPlayer patient = player(h, 4.5, 3.5);
        state(donor).bloodType = faygolover.rpmedicine.core.BloodType.A_POS;
        state(patient).bloodType = faygolover.rpmedicine.core.BloodType.O_POS;
        state(patient).bloodVolume = 3500;
        double donorBefore = state(donor).bloodVolume;
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.EMPTY_BLOOD_BAG.get(), 2));
        h.assertTrue(faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, donor, 0, null), "забор начался");
        h.runAfterDelay(1150, () -> {
            h.assertTrue(state(donor).bloodVolume <= donorBefore - 449, "у донора взяли 450 мл");
            int slot = -1;
            for (int i = 0; i < medic.getInventory().getContainerSize(); i++)
                if (medic.getInventory().getItem(i).is(faygolover.rpmedicine.registry.ModItems.BLOOD_BAG.get())) slot = i;
            h.assertTrue(slot >= 0, "у медика полный пакет");
            var bag = medic.getInventory().getItem(slot);
            h.assertTrue(faygolover.rpmedicine.item.BloodBagItem.type(bag) == faygolover.rpmedicine.core.BloodType.A_POS, "пакет подписан группой донора");
            long now = h.getLevel().getGameTime();
            h.assertTrue(!faygolover.rpmedicine.item.BloodBagItem.isSpoiled(bag, now), "свежий");
            h.assertTrue(faygolover.rpmedicine.item.BloodBagItem.isSpoiled(bag, now + 3 * 72000), "вне холодильника за 3 часа испортился");
            var cold = bag.copy();
            faygolover.rpmedicine.item.BloodBagItem.setCold(cold, true, now);
            h.assertTrue(!faygolover.rpmedicine.item.BloodBagItem.isSpoiled(cold, now + 3 * 72000), "в холодильнике за 3 часа цел");
            h.assertTrue(faygolover.rpmedicine.item.BloodBagItem.isSpoiled(cold, now + 8L * 24 * 72000), "за 8 дней испортился и в холодильнике");
            // Переливание несовместимой крови (A+ → O+).
            medic.getInventory().selected = slot < 9 ? slot : 0;
            if (slot >= 9) {
                medic.getInventory().setItem(0, bag.copy());
                medic.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
            int use = medic.getInventory().selected;
            h.assertTrue(faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, use, null), "переливание началось");
            h.runAfterDelay(400, () -> {
                MedicalState pm = state(patient);
                h.assertTrue(pm.bloodDripRemaining > 0 && pm.bloodDripType == faygolover.rpmedicine.core.BloodType.A_POS, "капает пакет A+");
                remove(h, medic, donor, patient);
                h.succeed();
            });
        });
    }

    /** Капельница идёт на ходу, пока рядом стойка. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void ivStandLetsDripRunWhileMoving(GameTestHelper h) {
        noDeath(false);
        testBlocks();
        BlockPos stand = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(stand, Blocks.IRON_BARS.defaultBlockState());
        ServerPlayer p = player(h, 3.5, 2.5);
        MedicalState m = state(p);
        m.salineDripRemaining = 500;
        m.salineDripRate = 5;
        HospitalService.tickPlayer(p, data(p));
        h.assertTrue(data(p).nearIvStand, "стойка рядом найдена");
        StepInput in = new StepInput(0.5);
        in.still = false;
        HospitalService.applyConditions(data(p), in, MedicalSettings.get());
        h.assertTrue(in.still, "со стойкой капельница не требует стоять на месте");
        remove(h, p);
        h.succeed();
    }
}
