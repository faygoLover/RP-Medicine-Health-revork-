package faygolover.rpmedicine.gametest;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.capability.MedicalNbt;
import faygolover.rpmedicine.core.BodyPart;
import faygolover.rpmedicine.core.Diagnostics;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Organ;
import faygolover.rpmedicine.core.Organs;
import faygolover.rpmedicine.core.WoundType;
import faygolover.rpmedicine.server.DamageHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static faygolover.rpmedicine.gametest.MedicalGameTests.T;
import static faygolover.rpmedicine.gametest.MedicalGameTests.noDeath;
import static faygolover.rpmedicine.gametest.MedicalGameTests.player;
import static faygolover.rpmedicine.gametest.MedicalGameTests.remove;
import static faygolover.rpmedicine.gametest.MedicalGameTests.state;

/** Сценарии третьего этапа «Операционная» (п. 13 ТЗ третьего этапа). */
@GameTestHolder(RpMedicine.MODID)
@PrefixGameTestTemplate(false)
public final class OperatingGameTests {
    private OperatingGameTests() {}

    /** Огнестрел в живот — урон органу; сканер, лаборатория, команда, сохранение. Своя пачка: меняет общий шанс. */
    @GameTest(template = T, batch = "organs", timeoutTicks = 100)
    public static void gunshotDamagesOrganScannerSeesIt(GameTestHelper h) {
        noDeath(false);
        MedicalSettings s = MedicalSettings.get();
        double chance = s.gunshotOrganChance;
        s.gunshotOrganChance = 1.0;
        try {
            ServerPlayer p = player(h, 2.5, 2.5);
            MedicalState m = state(p);
            InjuryProfile gun = new InjuryProfile("test/gunshot", WoundType.GUNSHOT, InjuryProfile.Location.HIT_POINT);
            DamageHandler.applyInjury(p, m, gun, 8, BodyPart.ABDOMEN, 0, null);
            h.assertTrue(Organs.worst(m) > 0, "орган задет");
            Organ hit = null;
            for (Organ o : Organ.VALUES) if (m.organ(o) > 0) hit = o;
            h.assertTrue(hit != null && hit.part == BodyPart.ABDOMEN, "орган живота");
            var words = Diagnostics.scanPart(m, BodyPart.ABDOMEN);
            Organ found = hit;
            h.assertTrue(words.stream().anyMatch(w -> w.startsWith("scan_" + found.id + "_")), "сканер видит: " + words);
            // Сохранение и чтение.
            MedicalState copy = new MedicalState(s);
            MedicalNbt.read(copy, MedicalNbt.write(m), s);
            h.assertTrue(Math.abs(copy.organ(hit) - m.organ(hit)) < 0.01, "органы сохраняются");
            // Команда ГМа.
            h.assertTrue(MedicalGameTests.command(h, "rpmedicine set " + p.getGameProfile().getName() + " organ heart 55") == 1, "set organ");
            h.assertTrue(m.organ(Organ.HEART) == 55, "сердце 55");
            h.assertTrue(Diagnostics.lab(m, s).troponin() > 14, "тропонин выше нормы");
            MedicalGameTests.command(h, "rpmedicine heal " + p.getGameProfile().getName());
            h.assertTrue(Organs.worst(m) == 0, "heal лечит органы");
            remove(h, p);
            h.succeed();
        } finally {
            s.gunshotOrganChance = chance;
        }
    }

    /** Операционный стол: аппарат ИВЛ и монитор; интубация без ларингоскопа нельзя, с ним — трубка стоит, SpO2 держится. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void intubationOnOperatingTable(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        HospitalGameTests.testBlocks();
        MedicalSettings s = MedicalSettings.get();
        net.minecraft.core.BlockPos table = h.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
        ServerPlayer patient = player(h, 3.5, 2.5);
        ServerPlayer medic = player(h, 3.5, 3.5);
        h.assertTrue(faygolover.rpmedicine.hospital.HospitalService.onUseBlock(patient, table), "лечь на стол");
        var d = faygolover.rpmedicine.server.Medical.data(patient);
        faygolover.rpmedicine.hospital.HospitalService.tickPlayer(patient, d);
        h.assertTrue(d.onTable && table.equals(d.monitorPos), "на столе, стол — монитор");
        h.assertTrue(faygolover.rpmedicine.hospital.HospitalService.monitorPatient(h.getLevel(), table) == patient, "стол показывает пациента");
        faygolover.rpmedicine.core.StepInput in = new faygolover.rpmedicine.core.StepInput(0.5);
        faygolover.rpmedicine.hospital.HospitalService.applyConditions(d, in, s);
        h.assertTrue(in.ventilator && in.oxygen, "аппарат и кислород");
        // Пропофол: наркоз, дыхание угнетено.
        MedicalState m = state(patient);
        m.addEffect(faygolover.rpmedicine.core.DrugEffect.ANESTHESIA, 1, 0, 600);
        m.addEffect(faygolover.rpmedicine.core.DrugEffect.RESP_DEPRESSION, 0.6, 0, 600);
        m.down = MedicalState.Down.FAINT;
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.ENDOTRACHEAL_TUBE.get(), 2));
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
        h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) == null, "без ларингоскопа не начать");
        medic.getInventory().setItem(5, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.LARYNGOSCOPE.get()));
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, null);
        h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) != null, "интубация началась");
        h.runAfterDelay(300, () -> {
            h.assertTrue(m.intubated, "трубка стоит");
            h.assertTrue(medic.getInventory().getItem(0).getCount() == 1, "трубка потрачена");
            // Минута физиологии на столе под пропофолом.
            for (int i = 0; i < 60 * 20; i++) MedicalGameTests.tickPatients(patient);
            h.assertTrue(m.down == MedicalState.Down.FAINT && m.intubated, "под наркозом, трубка на месте");
            h.assertTrue(m.spo2 >= s.spo2Normal - 3, "на столе дышит аппарат, SpO2 " + m.spo2);
            remove(h, patient, medic);
            h.succeed();
        });
    }

    /** Операция на столе: маска и перчатки, место — стол; скальпель вскрывает живот и становится нестерильным. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void incisionOnOperatingTable(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        HospitalGameTests.testBlocks();
        MedicalSettings s = MedicalSettings.get();
        net.minecraft.core.BlockPos table = h.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(table, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
        ServerPlayer patient = player(h, 3.5, 2.5);
        ServerPlayer medic = player(h, 3.5, 3.5);
        faygolover.rpmedicine.server.Medical.data(medic).skillOverride = 10;
        var scalpel = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SCALPEL.get());
        // Без стола, маски и перчаток — «пол», заражение выше.
        var floor = faygolover.rpmedicine.server.SurgeryService.context(medic, patient, scalpel);
        h.assertTrue(faygolover.rpmedicine.server.SurgeryService.place(medic, patient) == faygolover.rpmedicine.server.SurgeryService.Place.FLOOR, "место — пол");
        h.assertTrue(Math.abs(floor.infectionFactor() - s.surgeryFloorInfection * s.surgeryNoMaskFactor * s.surgeryNoGlovesFactor) < 1e-6, "пол без маски и перчаток");
        h.assertTrue(faygolover.rpmedicine.hospital.HospitalService.onUseBlock(patient, table), "лечь на стол");
        medic.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SURGICAL_MASK.get()));
        medic.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SURGICAL_GLOVES.get()));
        var ctx = faygolover.rpmedicine.server.SurgeryService.context(medic, patient, scalpel);
        h.assertTrue(Math.abs(ctx.infectionFactor() - s.surgeryTableInfection) < 1e-6 && ctx.successFactor() == s.surgeryTableSuccess, "стол, маска, перчатки: " + ctx);
        MedicalState m = state(patient);
        m.down = MedicalState.Down.FAINT;
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, scalpel);
        h.assertTrue(faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, BodyPart.ABDOMEN), "вскрытие началось");
        h.runAfterDelay(250, () -> {
            var ps = m.part(BodyPart.ABDOMEN);
            h.assertTrue(ps.surgery == faygolover.rpmedicine.core.BodyPartState.SurgeryStage.OPEN, "живот вскрыт, было " + ps.surgery);
            h.assertTrue(Math.abs(ps.surgeryContamination - s.surgeryTableInfection) < 1e-6, "загрязнение операции по месту: " + ps.surgeryContamination);
            h.assertTrue(!faygolover.rpmedicine.item.SurgicalInstrumentItem.isSterile(medic.getInventory().getItem(0)), "скальпель нестерилен");
            // Сохранение хода операции.
            MedicalState copy = new MedicalState(s);
            MedicalNbt.read(copy, MedicalNbt.write(m), s);
            h.assertTrue(copy.part(BodyPart.ABDOMEN).surgery == ps.surgery && copy.part(BodyPart.ABDOMEN).wounds.stream().anyMatch(w -> w.surgical),
                    "операция сохраняется");
            remove(h, patient, medic);
            h.succeed();
        });
    }

    /** Операция — всегда мини-игра на сцене тела, даже в бою; хорошее качество — шаг сделан. */
    @GameTest(template = T, batch = "minigames", timeoutTicks = 200)
    public static void surgeryStepIsAlwaysMinigame(GameTestHelper h) {
        noDeath(false);
        MedicalGameTests.noErrors();
        MedicalSettings.get().minigamesEnabled = true;
        ServerPlayer patient = player(h, 3.5, 2.5);
        ServerPlayer medic = player(h, 2.5, 2.5);
        faygolover.rpmedicine.server.Medical.data(medic).skillOverride = 10;
        MedicalState m = state(patient);
        m.down = MedicalState.Down.FAINT;
        // «Бой»: пациента только что ранили — обычное лечение шло бы прогресс-баром, операция — нет.
        patient.hurt(patient.damageSources().generic(), 1f);
        medic.getInventory().selected = 0;
        medic.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SCALPEL.get()));
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, BodyPart.ABDOMEN);
        int session = faygolover.rpmedicine.server.MinigameService.currentSession(medic);
        h.assertTrue(session > 0, "разрез — мини-игра, даже в бою");
        h.runAfterDelay(50, () -> {
            faygolover.rpmedicine.server.MinigameService.onResult(medic, session, 0.95f);
            h.runAfterDelay(3, () -> {
                h.assertTrue(m.part(BodyPart.ABDOMEN).surgery == faygolover.rpmedicine.core.BodyPartState.SurgeryStage.OPEN, "вскрыто после мини-игры");
                MedicalSettings.get().minigamesEnabled = false;
                remove(h, patient, medic);
                h.succeed();
            });
        });
    }

    /** Изъятие органа: выбор органа, контейнер с органом у хирурга; пересадка обратно возвращает пустой контейнер. */
    @GameTest(template = T, timeoutTicks = 1200)
    public static void organRemovalAndTransplant(GameTestHelper h) {
        noDeath(true);
        MedicalGameTests.noErrors();
        ServerPlayer patient = player(h, 3.5, 2.5);
        ServerPlayer medic = player(h, 2.5, 2.5);
        faygolover.rpmedicine.server.Medical.data(medic).skillOverride = 10;
        MedicalState m = state(patient);
        m.down = MedicalState.Down.FAINT;
        m.part(BodyPart.CHEST).surgery = faygolover.rpmedicine.core.BodyPartState.SurgeryStage.RETRACTED;
        var inv = medic.getInventory();
        inv.selected = 0;
        inv.setItem(0, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.ORGAN_CONTAINER.get(), 2));
        // Связки режет скальпель — без него изъятие не начать.
        inv.setItem(7, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SCALPEL.get()));
        // В груди два органа — сначала выбор, действия ещё нет.
        faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, BodyPart.CHEST);
        h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) == null, "ждём выбора органа");
        faygolover.rpmedicine.server.TreatmentService.onOrganChoice(medic, patient.getId(), 0, BodyPart.CHEST, "lungs");
        h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) != null, "изъятие началось");
        h.runAfterDelay(500, () -> {
            h.assertTrue(!m.hasOrgan(Organ.LUNGS) && m.hasOrgan(Organ.HEART), "изъяты лёгкие");
            int organSlot = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(faygolover.rpmedicine.registry.ModItems.ORGAN.get())) organSlot = i;
            h.assertTrue(organSlot >= 0 && faygolover.rpmedicine.item.OrganItem.organ(inv.getItem(organSlot)) == Organ.LUNGS, "лёгкие в контейнере у хирурга");
            h.assertTrue(inv.getItem(0).getCount() == 1, "один контейнер потрачен");
            var organ = inv.getItem(organSlot).copy();
            inv.setItem(organSlot, net.minecraft.world.item.ItemStack.EMPTY);
            inv.setItem(0, organ);
            inv.setItem(1, new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.ORGAN_CONTAINER.get()));
            faygolover.rpmedicine.server.TreatmentService.startWithItem(medic, patient, 0, BodyPart.CHEST);
            h.assertTrue(faygolover.rpmedicine.server.ActionManager.current(medic) != null, "пересадка началась");
            h.runAfterDelay(600, () -> {
                h.assertTrue(m.hasOrgan(Organ.LUNGS), "лёгкие на месте");
                int containers = 0;
                for (int i = 0; i < inv.getContainerSize(); i++)
                    if (inv.getItem(i).is(faygolover.rpmedicine.registry.ModItems.ORGAN_CONTAINER.get())) containers += inv.getItem(i).getCount();
                h.assertTrue(containers == 2, "пустой контейнер вернулся, было " + containers);
                remove(h, patient, medic);
                h.succeed();
            });
        });
    }

    /** Износ перчаток (замечание 09.10, И38): после шага у одного пациента на другом они грязные и хуже, чем без них. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void glovesGetDirtyBetweenPatients(GameTestHelper h) {
        MedicalSettings s = MedicalSettings.get();
        ServerPlayer a = player(h, 3.5, 2.5);
        ServerPlayer b = player(h, 1.5, 2.5);
        ServerPlayer medic = player(h, 3.5, 3.5);
        var gloves = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SURGICAL_GLOVES.get());
        medic.getInventory().setItem(8, gloves);
        var scalpel = new net.minecraft.world.item.ItemStack(faygolover.rpmedicine.registry.ModItems.SCALPEL.get());
        double clean = faygolover.rpmedicine.server.SurgeryService.context(medic, a, scalpel).infectionFactor();
        faygolover.rpmedicine.server.SurgeryService.stepDone(medic, a);
        h.assertTrue(!faygolover.rpmedicine.item.Wear.dirty(medic.getInventory().getItem(8)), "после шага у того же пациента — чистые");
        double other = faygolover.rpmedicine.server.SurgeryService.context(medic, b, scalpel).infectionFactor();
        h.assertTrue(Math.abs(other - clean * s.surgeryNoGlovesFactor * s.dirtyGlovesFactor) < 1e-6, "на другом пациенте — грязные: " + clean + " -> " + other);
        faygolover.rpmedicine.server.SurgeryService.stepDone(medic, b);
        h.assertTrue(faygolover.rpmedicine.item.Wear.dirty(medic.getInventory().getItem(8)) && !faygolover.rpmedicine.server.SurgeryService.hasGloves(medic),
                "перчатки помечены грязными");
        remove(h, a, b, medic);
        h.succeed();
    }
}
