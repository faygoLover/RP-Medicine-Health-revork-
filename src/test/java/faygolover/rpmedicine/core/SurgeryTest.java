package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.SurgeryStage;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Пошаговая хирургия (ТЗ третьего этапа, п. 4). */
class SurgeryTest {

    private static Treatments.Result step(MedicalState m, BodyPart p, TreatmentAction a, MedicalSettings s) {
        assertNull(Treatments.check(m, p, a, s), a + " должно быть можно");
        return Treatments.apply(m, p, a, false, new SplittableRandom(1), s, Surgery.Context.DEFAULT);
    }

    private static MedicalState unconscious(MedicalSettings s) {
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        return m;
    }

    @Test
    void consciousPatientCannotBeOpened() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("patient_moves", Treatments.check(m, BodyPart.ABDOMEN, TreatmentAction.INCISE, s));
        m.part(BodyPart.ABDOMEN).localAnesthesiaSeconds = 600;
        assertNull(Treatments.check(m, BodyPart.ABDOMEN, TreatmentAction.INCISE, s), "местная анестезия части — можно");
    }

    @Test
    void stepsGoInOrderAndBleedingFollowsClamp() {
        MedicalSettings s = settings();
        MedicalState m = unconscious(s);
        BodyPartState ps = m.part(BodyPart.ABDOMEN);
        assertEquals("not_open", Treatments.check(m, BodyPart.ABDOMEN, TreatmentAction.CLAMP, s));
        step(m, BodyPart.ABDOMEN, TreatmentAction.INCISE, s);
        assertEquals(SurgeryStage.OPEN, ps.surgery);
        assertTrue(ps.wounds.stream().anyMatch(w -> w.surgical), "операционный разрез");
        double open = Physiology.partExternalBleed(m, ps, s);
        assertTrue(open >= s.openPartBleed, "вскрыто без зажима — сильно кровит: " + open);
        assertEquals("clamp_first", Treatments.check(m, BodyPart.ABDOMEN, TreatmentAction.RETRACT, s));
        step(m, BodyPart.ABDOMEN, TreatmentAction.CLAMP, s);
        assertTrue(Physiology.partExternalBleed(m, ps, s) < open - s.openPartBleed + s.clampedPartBleed + 1, "зажато — кровит мало");
        step(m, BodyPart.ABDOMEN, TreatmentAction.RETRACT, s);
        assertEquals(SurgeryStage.RETRACTED, ps.surgery);
    }

    @Test
    void sutureOnRetractedPartStopsBleedingRepairsOrganThenCloses() {
        MedicalSettings s = settings();
        MedicalState m = unconscious(s);
        BodyPartState ps = m.part(BodyPart.ABDOMEN);
        ps.internalBleed = 120;
        m.organs[Organ.LIVER.ordinal()] = 30;
        step(m, BodyPart.ABDOMEN, TreatmentAction.INCISE, s);
        step(m, BodyPart.ABDOMEN, TreatmentAction.CLAMP, s);
        step(m, BodyPart.ABDOMEN, TreatmentAction.RETRACT, s);
        assertEquals("internal_stopped", step(m, BodyPart.ABDOMEN, TreatmentAction.SUTURE, s).key);
        assertEquals(0, ps.internalBleed, 1e-9);
        assertEquals("organ_repaired", step(m, BodyPart.ABDOMEN, TreatmentAction.SUTURE, s).key);
        assertEquals(Math.max(0, 30 - s.organRepairAmount), m.organs[Organ.LIVER.ordinal()], 1e-9);
        assertEquals("surgery_closed", step(m, BodyPart.ABDOMEN, TreatmentAction.SUTURE, s).key);
        assertEquals(SurgeryStage.NONE, ps.surgery);
        assertTrue(ps.wounds.stream().filter(w -> w.surgical).allMatch(w -> w.sutured), "разрез зашит");
        assertEquals(0, Surgery.openBleed(ps, s), 1e-9);
    }

    @Test
    void specialStepsOnRetractedPart() {
        MedicalSettings s = settings();
        MedicalState m = unconscious(s);
        BodyPartState leg = m.part(BodyPart.LEFT_LEG);
        leg.arterial = true;
        leg.fracture = BodyPartState.Fracture.CLOSED;
        leg.bullets = 2;
        leg.fragments = 1;
        assertEquals("not_retracted", Treatments.check(m, BodyPart.LEFT_LEG, TreatmentAction.VESSEL_SUTURE, s));
        step(m, BodyPart.LEFT_LEG, TreatmentAction.INCISE, s);
        step(m, BodyPart.LEFT_LEG, TreatmentAction.CLAMP, s);
        step(m, BodyPart.LEFT_LEG, TreatmentAction.RETRACT, s);
        step(m, BodyPart.LEFT_LEG, TreatmentAction.VESSEL_SUTURE, s);
        assertFalse(leg.arterial, "артерия сшита");
        step(m, BodyPart.LEFT_LEG, TreatmentAction.OSTEOSYNTHESIS, s);
        assertTrue(leg.fixated, "перелом зафиксирован");
        assertEquals("foreign_all_removed", step(m, BodyPart.LEFT_LEG, TreatmentAction.TWEEZERS, s).key);
        assertFalse(leg.hasForeignBodies(), "все пули и осколки за раз");

        // Остеосинтез: заживает без шины и быстрее, чем с шиной.
        MedicalState splinted = new MedicalState(s);
        splinted.part(BodyPart.LEFT_LEG).fracture = BodyPartState.Fracture.CLOSED;
        splinted.part(BodyPart.LEFT_LEG).splint = true;
        assertTrue(Healing.fractureHealSeconds(leg, s) < Healing.fractureHealSeconds(splinted.part(BodyPart.LEFT_LEG), s) / 2);

        MedicalState chest = unconscious(s);
        chest.pneumo = MedicalState.Pneumo.TENSION;
        assertEquals("chest_only", Treatments.check(chest, BodyPart.ABDOMEN, TreatmentAction.DRAIN, s));
        step(chest, BodyPart.CHEST, TreatmentAction.INCISE, s);
        step(chest, BodyPart.CHEST, TreatmentAction.CLAMP, s);
        step(chest, BodyPart.CHEST, TreatmentAction.RETRACT, s);
        step(chest, BodyPart.CHEST, TreatmentAction.DRAIN, s);
        assertEquals(MedicalState.Pneumo.NONE, chest.pneumo, "дренаж снял пневмоторакс");
    }

    @Test
    void stepWithoutAnesthesiaHurtsAndErrorDamagesOrgan() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.restrained = true;
        step(m, BodyPart.CHEST, TreatmentAction.INCISE, s);
        assertTrue(m.acutePain >= s.surgeryStepPain - 1e-9, "зафиксирован, без обезболивания — боль");

        MedicalState e = unconscious(s);
        BodyPartState ps = e.part(BodyPart.CHEST);
        ps.surgery = SurgeryStage.RETRACTED;
        Treatments.Result r = Treatments.apply(e, BodyPart.CHEST, TreatmentAction.DRAIN, true, new SplittableRandom(2), s, Surgery.Context.DEFAULT);
        assertEquals("surgery_slip", r.key);
        assertTrue(e.organs[Organ.HEART.ordinal()] + e.organs[Organ.LUNGS.ordinal()] > 0, "ошибка на раскрытой груди задевает орган");
    }

    @Test
    void openPartGetsInfectedWhenDirty() {
        MedicalSettings s = settings();
        s.openPartInfectionPerMinute = 1.0;
        MedicalState m = unconscious(s);
        Treatments.apply(m, BodyPart.ABDOMEN, TreatmentAction.INCISE, false, new SplittableRandom(1), s, new Surgery.Context(true, 1, 5));
        run(m, input(3), s, 61);
        assertTrue(m.part(BodyPart.ABDOMEN).wounds.stream().anyMatch(w -> w.surgical && w.isInfected()), "открытая грязная рана заразилась");
    }
}
