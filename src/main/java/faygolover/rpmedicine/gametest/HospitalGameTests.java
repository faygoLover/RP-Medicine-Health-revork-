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
                HospitalFunction.IV_STAND, List.of("minecraft:iron_bars"),
                HospitalFunction.LAB, List.of("minecraft:crafting_table"),
                HospitalFunction.STERILIZER, List.of("minecraft:furnace")), Map.of());
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

    /** Заражённая рана и антибиотик из датапака drugs: таблетка даёт эффект, инфекция спадает (−6 % в час). */
    @GameTest(template = T, timeoutTicks = 400)
    public static void infectionTreatedWithAntibiotic(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        ServerPlayer medic = player(h, 3.5, 3.5);
        ServerPlayer patient = player(h, 2.5, 3.5);
        MedicalState m = state(patient);
        var w = new faygolover.rpmedicine.core.Wound(faygolover.rpmedicine.core.WoundType.BURN, 30);
        w.infectionStage = faygolover.rpmedicine.core.Wound.Infection.INFECTED;
        w.infection = 50;
        m.part(faygolover.rpmedicine.core.BodyPart.LEFT_ARM).wounds.add(w);
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.AMOXICILLIN.get(), 3));
        h.assertTrue(faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null), "дать таблетку");
        h.runAfterDelay(120, () -> {
            h.assertTrue(m.hasEffect(faygolover.rpmedicine.core.DrugEffect.ANTIBIOTIC), "антибиотик действует");
            h.assertTrue(medic.getInventory().getItem(0).getCount() == 2, "одна таблетка потрачена");
            faygolover.rpmedicine.core.Healing.fastForward(m, 7 * 3600, MedicalSettings.get());
            h.assertTrue(!w.isInfected() || w.infection < 15, "под антибиотиком инфекция спадает, было " + w.infection);
            remove(h, medic, patient);
            h.succeed();
        });
    }

    /** Приборы и лаборатория: гемоанализатор без ланцета не работает; шприц — пробирка; анализ на столе. */
    @GameTest(template = T, timeoutTicks = 2000)
    public static void diagnosticsAndLab(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        testBlocks();
        BlockPos lab = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(lab, Blocks.CRAFTING_TABLE.defaultBlockState());
        ServerPlayer medic = player(h, 2.5, 2.5);
        ServerPlayer patient = player(h, 3.5, 2.5);
        state(patient).bloodType = faygolover.rpmedicine.core.BloodType.O_POS;
        var inv = medic.getInventory();
        inv.selected = 0;
        inv.setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.HEMOANALYZER.get()));
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
        h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) == null, "без ланцета гемоанализатор не запускается");
        inv.setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.BLOOD_DRAW_SYRINGE.get()));
        h.assertTrue(faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null), "забор в пробирку");
        h.runAfterDelay(140, () -> {
            h.assertTrue(!inv.contains(new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.BLOOD_DRAW_SYRINGE.get())), "шприц потрачен");
            int tube = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(faygolover.rpmedicine.registry.ModItems.BLOOD_SAMPLE.get())) tube = i;
            h.assertTrue(tube >= 0, "пробирка у медика");
            var sample = inv.getItem(tube).copy();
            h.assertTrue(patient.getName().getString().equals(faygolover.rpmedicine.server.LabService.patientName(sample)), "подписана пациентом");
            // Совместимость с пакетом во второй руке: A+ пациенту O+ нельзя.
            var bag = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.BLOOD_BAG.get());
            faygolover.rpmedicine.item.BloodBagItem.fill(bag, faygolover.rpmedicine.core.BloodType.A_POS, h.getLevel().getGameTime(), "donor");
            var m = new MedicalState(MedicalSettings.get());
            faygolover.rpmedicine.capability.MedicalNbt.read(m, sample.getTag().getCompound("Medical"), MedicalSettings.get());
            var lines = faygolover.rpmedicine.server.LabService.report(sample, faygolover.rpmedicine.core.Diagnostics.lab(m, MedicalSettings.get()), bag);
            h.assertTrue(lines.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && tc.getKey().equals("rpmedicine.lab.compat_no")), "лаборатория: пакет несовместим");
            int tubeSlot = tube;
            inv.setItem(tubeSlot, net.minecraft.world.item.ItemStack.EMPTY);
            inv.setItem(0, sample);
            inv.selected = 0;
            h.assertTrue(faygolover.rpmedicine.server.LabService.onUseBlock(medic, lab), "анализ на лабораторном столе начался");
            h.runAfterDelay(1250, () -> {
                h.assertTrue(inv.getItem(0).isEmpty(), "пробирка ушла в анализ");
                remove(h, medic, patient);
                h.succeed();
            });
        });
    }

    /** Пинцет: пуля извлечена, инструмент нестерилен, стерилизатор возвращает стерильность; вправление вывиха с панели. */
    @GameTest(template = T, timeoutTicks = 800)
    public static void tweezersSterilizerAndReduction(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        testBlocks();
        BlockPos sterilizer = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(sterilizer, Blocks.FURNACE.defaultBlockState());
        ServerPlayer medic = player(h, 2.5, 2.5);
        ServerPlayer patient = player(h, 3.5, 2.5);
        MedicalState m = state(patient);
        m.part(faygolover.rpmedicine.core.BodyPart.ABDOMEN).bullets = 1;
        m.part(faygolover.rpmedicine.core.BodyPart.ABDOMEN).wounds.add(new faygolover.rpmedicine.core.Wound(faygolover.rpmedicine.core.WoundType.GUNSHOT, 20));
        m.part(faygolover.rpmedicine.core.BodyPart.LEFT_ARM).dislocated = true;
        m.morphineSeconds = 900; // без шока от боли
        var inv = medic.getInventory();
        inv.selected = 0;
        inv.setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SURGICAL_TWEEZERS.get()));
        h.assertTrue(faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(inv.getItem(0)), "новый пинцет стерилен");
        h.assertTrue(faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null), "извлечение началось");
        h.runAfterDelay(300, () -> {
            h.assertTrue(m.part(faygolover.rpmedicine.core.BodyPart.ABDOMEN).bullets == 0, "пуля извлечена");
            h.assertTrue(!faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(inv.getItem(0)), "пинцет после раны нестерилен");
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(sterilizer), net.minecraft.core.Direction.UP, sterilizer, false);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                    medic, net.minecraft.world.InteractionHand.MAIN_HAND, sterilizer, hit));
            h.assertTrue(faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(inv.getItem(0)), "стерилизатор");
            inv.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
            faygolover.rpmedicine.server.TreatmentService.startHandAction(medic, patient, faygolover.rpmedicine.core.BodyPart.LEFT_ARM,
                    faygolover.rpmedicine.core.TreatmentAction.REDUCE, 4, 2);
            h.runAfterDelay(200, () -> {
                h.assertTrue(!m.part(faygolover.rpmedicine.core.BodyPart.LEFT_ARM).dislocated, "вывих вправлен");
                remove(h, medic, patient);
                h.succeed();
            });
        });
    }

    /** Мини-игра вне боя: качество из ответа клиента, слишком быстрый ответ — провал, отказ — прогресс-бар; в бою — сразу прогресс-бар. */
    @GameTest(template = T, batch = "minigames", timeoutTicks = 600)
    public static void minigameOutsideCombat(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        MedicalSettings.get().minigamesEnabled = true;
        ServerPlayer medic = player(h, 2.5, 2.5);
        ServerPlayer patient = player(h, 3.5, 2.5);
        MedicalState m = state(patient);
        var w = new faygolover.rpmedicine.core.Wound(faygolover.rpmedicine.core.WoundType.CUT, 20);
        m.part(faygolover.rpmedicine.core.BodyPart.LEFT_ARM).wounds.add(w);
        var inv = medic.getInventory();
        inv.selected = 0;
        inv.setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.BANDAGE.get(), 4));
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
        int session = faygolover.rpmedicine.server.MinigameService.currentSession(medic);
        h.assertTrue(session > 0, "вне боя — мини-игра");
        // Ответ мгновенно — подделка: провал (повязка плохая).
        faygolover.rpmedicine.server.MinigameService.onResult(medic, session, 1f);
        h.runAfterDelay(3, () -> {
            h.assertTrue(w.isDressed() && w.dressingQuality < 0.75, "слишком быстрый ответ — ошибка, качество " + w.dressingQuality);
            w.removeDressing();
            faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
            int s2 = faygolover.rpmedicine.server.MinigameService.currentSession(medic);
            h.assertTrue(s2 > 0, "вторая мини-игра началась: действие " + faygolover.rpmedicine.server.ActionManager.current(medic)
                    + ", повязка " + w.dressing + ", бинтов " + inv.getItem(0));
            h.runAfterDelay(50, () -> {
                h.assertTrue(faygolover.rpmedicine.server.MinigameService.currentSession(medic) == s2,
                        "мини-игра ещё идёт, сейчас " + faygolover.rpmedicine.server.ActionManager.current(medic));
                faygolover.rpmedicine.server.MinigameService.onResult(medic, s2, 0.9f);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(w.isDressed() && w.dressingQuality > 0.9, "хорошая мини-игра — хорошая повязка, " + w.dressingQuality);
                    w.removeDressing();
                    faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
                    int s3 = faygolover.rpmedicine.server.MinigameService.currentSession(medic);
                    faygolover.rpmedicine.server.MinigameService.onResult(medic, s3, -1f);
                    h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) != null
                            && faygolover.rpmedicine.server.MinigameService.currentSession(medic) < 0, "отказ — прогресс-бар");
                    faygolover.rpmedicine.server.ActionManager.cancel(medic, null);
                    // Бой: пациента только что ранили — сразу прогресс-бар.
                    patient.hurt(patient.damageSources().generic(), 1f);
                    faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
                    h.assertTrue(faygolover.rpmedicine.server.MinigameService.currentSession(medic) < 0
                            && faygolover.rpmedicine.server.ActionManager.current(medic) != null, "в бою — прогресс-бар");
                    MedicalSettings.get().minigamesEnabled = false;
                    remove(h, medic, patient);
                    h.succeed();
                });
            });
        });
    }

    /** Медкарта: привязка пустой карты, предложение после огнестрела, принять и отклонить, команда ГМа, файл на сервере. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void medcardProposalsAndGmFields(GameTestHelper h) {
        noDeath(false);
        ServerPlayer medic = player(h, 2.5, 2.5);
        ServerPlayer patient = player(h, 3.5, 2.5);
        var blank = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.MEDCARD.get(), 3);
        medic.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blank);
        blank.getItem().interactLivingEntity(blank, medic, patient, net.minecraft.world.InteractionHand.MAIN_HAND);
        var card = medic.getInventory().items.stream().filter(st -> faygolover.rpmedicine.item.MedcardItem.owner(st) != null).findFirst();
        h.assertTrue(card.isPresent() && patient.getUUID().equals(faygolover.rpmedicine.item.MedcardItem.owner(card.get())), "карта привязана к пациенту");
        h.assertTrue(medic.getMainHandItem().getCount() == 2, "из стопки ушла одна карта");

        // Огнестрел в ногу — мод предлагает запись (одну, даже если попаданий несколько).
        var rep = new faygolover.rpmedicine.core.Injuries.Report();
        var w = new faygolover.rpmedicine.core.Wound(faygolover.rpmedicine.core.WoundType.GUNSHOT, 20);
        rep.outcomes.add(faygolover.rpmedicine.core.Injuries.Outcome.WOUND);
        rep.wounds.add(w);
        faygolover.rpmedicine.server.MedcardHooks.injury(patient, rep, faygolover.rpmedicine.core.BodyPart.LEFT_LEG);
        faygolover.rpmedicine.server.MedcardHooks.injury(patient, rep, faygolover.rpmedicine.core.BodyPart.LEFT_LEG);
        var server = h.getLevel().getServer();
        var mc = faygolover.rpmedicine.medcard.MedcardStore.get(server, patient.getUUID());
        h.assertTrue(mc.entries.size() == 1 && mc.entries.get(0).proposed && mc.entries.get(0).key.equals("gunshot"), "одно предложение «огнестрел»");
        int id = mc.entries.get(0).id;
        medic.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, card.get().copy());
        faygolover.rpmedicine.medcard.MedcardService.onAction(medic, new faygolover.rpmedicine.network.MedcardActionPacket(
                patient.getUUID(), faygolover.rpmedicine.network.MedcardActionPacket.Op.EDIT, id, "Огнестрел левого бедра, пуля извлечена"));
        h.assertTrue(!mc.entries.get(0).proposed && mc.entries.get(0).text.startsWith("Огнестрел"), "правка принимает запись");
        faygolover.rpmedicine.medcard.MedcardService.propose(server, patient, "clinical_death", List.of(), "");
        int id2 = mc.entries.get(1).id;
        faygolover.rpmedicine.medcard.MedcardService.onAction(medic, new faygolover.rpmedicine.network.MedcardActionPacket(
                patient.getUUID(), faygolover.rpmedicine.network.MedcardActionPacket.Op.DECLINE, id2, ""));
        h.assertTrue(mc.entries.size() == 1, "отклонённое предложение удалено");
        // Без карты в руках — правка не проходит.
        medic.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        faygolover.rpmedicine.medcard.MedcardService.onAction(medic, new faygolover.rpmedicine.network.MedcardActionPacket(
                patient.getUUID(), faygolover.rpmedicine.network.MedcardActionPacket.Op.ADD, -1, "чужая запись"));
        h.assertTrue(mc.entries.size() == 1, "без карты — нельзя");
        // ГМ меняет группу и вес.
        faygolover.rpmedicine.medcard.MedcardService.gmSet(server, patient.getUUID(), patient.getGameProfile().getName(), "blood_type", "ab-");
        faygolover.rpmedicine.medcard.MedcardService.gmSet(server, patient.getUUID(), patient.getGameProfile().getName(), "weight", "90");
        h.assertTrue(state(patient).bloodType == faygolover.rpmedicine.core.BloodType.AB_NEG && state(patient).weightKg == 90, "группа и вес — и в карте, и у персонажа");
        java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("rpmedicine/cards/" + patient.getUUID() + ".json");
        h.runAfterDelay(20, () -> {
            h.assertTrue(java.nio.file.Files.exists(file), "файл медкарты на сервере");
            remove(h, medic, patient);
            h.succeed();
        });
    }

    /** ГМ: уровень «Медицины» командой, журнал и сводка, история и график, панель (вылечить, поднять). */
    @GameTest(template = T, timeoutTicks = 300)
    public static void gmSkillStatsHistoryPanel(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        var server = h.getLevel().getServer();
        ServerPlayer gm = player(h, 1.5, 1.5);
        ServerPlayer medic = player(h, 2.5, 2.5);
        ServerPlayer patient = player(h, 3.5, 2.5);
        // Тестовый сервер даёт операторам уровень 0 — задаём уровень явно.
        server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(gm.getGameProfile(), 4, false));
        h.assertTrue(MedicalGameTests.command(h, "rpmedicine skill " + medic.getGameProfile().getName() + " 6") == 1, "команда skill");
        h.assertTrue(Medical.medicineLevel(medic) == 6, "свой уровень «Медицины» вместо перков, было " + Medical.medicineLevel(medic));
        // Лечение попадает в журнал.
        m(patient).part(faygolover.rpmedicine.core.BodyPart.LEFT_ARM).wounds.add(new faygolover.rpmedicine.core.Wound(faygolover.rpmedicine.core.WoundType.CUT, 20));
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.BANDAGE.get(), 2));
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
        // История: снимки раз в 10 секунд.
        for (int i = 0; i < 3; i++) faygolover.rpmedicine.stats.History.record(patient.getUUID(), m(patient));
        h.runAfterDelay(120, () -> {
            var summary = faygolover.rpmedicine.stats.StatsService.summary(server, medic.getUUID(), medic.getGameProfile().getName(), 1);
            h.assertTrue(summary.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && tc.getKey().equals("rpmedicine.stats.treat_given") && tc.getArgs().length > 0 && ((Integer) tc.getArgs()[0]) >= 1), "в сводке медика есть лечение");
            var graph = faygolover.rpmedicine.stats.History.graph(patient.getUUID(), "p");
            h.assertTrue(graph.size() == 8, "график: заголовок и 7 показателей, было " + graph.size());
            // Панель ГМа: тяжёлый пациент — поднять, затем вылечить.
            m(patient).bloodVolume = 1500;
            m(patient).down = MedicalState.Down.KNOCKDOWN;
            h.assertTrue(faygolover.rpmedicine.server.GmPanelService.colorOf(patient) == 3, "цвет «лежит»");
            faygolover.rpmedicine.server.GmPanelService.onAction(gm, new faygolover.rpmedicine.network.GmActionPacket(patient.getUUID(),
                    faygolover.rpmedicine.network.GmActionPacket.Action.REVIVE));
            h.assertTrue(!m(patient).isDown(), "поднят");
            faygolover.rpmedicine.server.GmPanelService.onAction(gm, new faygolover.rpmedicine.network.GmActionPacket(patient.getUUID(),
                    faygolover.rpmedicine.network.GmActionPacket.Action.HEAL));
            h.assertTrue(m(patient).isQuiet(MedicalSettings.get()), "вылечен");
            // Не оператор — действия не проходят.
            m(patient).bloodVolume = 3000;
            faygolover.rpmedicine.server.GmPanelService.onAction(medic, new faygolover.rpmedicine.network.GmActionPacket(patient.getUUID(),
                    faygolover.rpmedicine.network.GmActionPacket.Action.HEAL));
            h.assertTrue(m(patient).bloodVolume == 3000, "не оператор — нельзя");
            MedicalGameTests.command(h, "rpmedicine skill " + medic.getGameProfile().getName() + " reset");
            server.getPlayerList().getOps().remove(gm.getGameProfile());
            remove(h, gm, medic, patient);
            h.succeed();
        });
    }

    private static MedicalState m(ServerPlayer p) {
        return state(p);
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
