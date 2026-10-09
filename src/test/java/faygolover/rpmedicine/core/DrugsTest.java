package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап, п. 6: механика доз, передозировка, налоксон, наружные средства. */
class DrugsTest {

    private static Drug.Dose dose(DrugEffect e, double strength, double delay, double seconds) {
        return new Drug.Dose(e, strength, delay, seconds);
    }

    private static final Drug PARACETAMOL = new Drug("test:paracetamol", Drug.Form.PILL,
            List.of(dose(DrugEffect.ANALGESIA, 12, 60, 1800), dose(DrugEffect.ANTIPYRETIC, 0.7, 300, 14400)),
            2, 24 * 3600, List.of(dose(DrugEffect.PRESSURE, -15, 0, 3600)), 0, false, Drug.Special.NONE);
    private static final Drug TRAMADOL = new Drug("test:tramadol", Drug.Form.PILL,
            List.of(dose(DrugEffect.ANALGESIA, 35, 0, 2400), dose(DrugEffect.RESP_DEPRESSION, 0.1, 0, 2400)),
            2, 12 * 3600, List.of(), 0, true, Drug.Special.NONE);
    private static final Drug DIAZEPAM = new Drug("test:diazepam", Drug.Form.INJECTION,
            List.of(dose(DrugEffect.SEDATION, 40, 0, 1800)), 0, 0, List.of(), 0, false, Drug.Special.NONE);
    private static final Drug NALOXONE = new Drug("test:naloxone", Drug.Form.INJECTION, List.of(), 0, 0, List.of(), 0, false,
            Drug.Special.OPIOID_ANTIDOTE);
    private static final Drug ANTISEPTIC = new Drug("test:antiseptic", Drug.Form.TOPICAL, List.of(), 0, 0, List.of(), 0, false,
            Drug.Special.ANTISEPTIC);
    private static final Drug OINTMENT = new Drug("test:ointment", Drug.Form.TOPICAL, List.of(), 0, 0, List.of(), 0, false,
            Drug.Special.ANTIBIOTIC_OINTMENT);

    private static Treatments.Result give(MedicalState m, Drug d, MedicalSettings s) {
        return Treatments.apply(m, BodyPart.CHEST, d.form() == Drug.Form.TOPICAL ? TreatmentAction.DRUG_TOPICAL : TreatmentAction.DRUG,
                false, new Random(1), s, d);
    }

    @Test
    void effectStartsAfterDelayAndEnds() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).wounds.add(new Wound(WoundType.BRUISE, 40));
        run(m, input(1), s, 5);
        double rawPain = m.pain;
        give(m, PARACETAMOL, s);
        run(m, input(1), s, 3);
        assertEquals(rawPain, m.pain, 1.5, "таблетка ещё не всосалась");
        run(m, input(1), s, 120);
        assertTrue(m.pain < rawPain - 10, "через 1–2 минуты боль ниже");
        Healing.fastForward(m, 6 * 3600, s);
        assertEquals(0, m.effect(DrugEffect.ANALGESIA), "препарат вышел — обезболивание закончилось");
    }

    @Test
    void overdoseWhenLimitExceededWithinWindow() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("pill_taken", give(m, PARACETAMOL, s).key);
        assertEquals("pill_taken", give(m, PARACETAMOL, s).key);
        assertEquals("drug_overdose", give(m, PARACETAMOL, s).key, "три дозы сразу — в крови больше порога");
        run(m, input(1), s, 120);
        assertTrue(m.hasEffect(DrugEffect.PRESSURE), "эффект передозировки, когда всосалось");
        // Препарат выводится: через сутки та же доза уже не передозировка.
        MedicalState fresh = new MedicalState(s);
        give(fresh, PARACETAMOL, s);
        give(fresh, PARACETAMOL, s);
        Healing.fastForward(fresh, 24 * 3600 + 60, s);
        assertTrue(fresh.drugLevels.isEmpty(), "препарат вышел из крови");
        assertEquals("pill_taken", give(fresh, PARACETAMOL, s).key);
    }

    @Test
    void pillsOnlyForConsciousPatient() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        assertEquals("must_be_conscious", Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, PARACETAMOL));
        m.seizureSeconds = 10; // есть показание — иначе «не нужно» (замечание 09.10, М5)
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, DIAZEPAM), "укол — можно");
    }

    @Test
    void drugNeedCheckedBeforeDose() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("no_agitation", Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, DIAZEPAM), "здоровому успокоительное не нужно");
        assertEquals("no_pain", Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, PARACETAMOL), "без боли и жара не нужно");
        assertTrue(Treatments.FORCEABLE.contains("no_agitation") && Treatments.FORCEABLE.contains("no_opioids"), "можно продавить повтором");
        m.rawPain = 20;
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, PARACETAMOL), "есть боль — нужно");
    }

    @Test
    void sedationWithOpioidsDepressesBreathingAndNaloxoneReverses() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("no_opioids", Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, NALOXONE));
        give(m, TRAMADOL, s);
        run(m, input(2), s, 120);
        double alone = Physiology.respiratoryDepression(m);
        give(m, DIAZEPAM, s);
        run(m, input(2), s, 120);
        assertTrue(Physiology.respiratoryDepression(m) >= alone + 0.29, "седация с опиатом угнетает дыхание");
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG, s, NALOXONE));
        assertEquals("antidote_given", give(m, NALOXONE, s).key);
        assertEquals(0, m.opioidSeconds);
        assertFalse(m.hasEffect(DrugEffect.ANALGESIA), "обезболивание опиатом снято");
        assertFalse(m.hasEffect(DrugEffect.RESP_DEPRESSION));
        assertTrue(m.hasEffect(DrugEffect.SEDATION), "диазепам остался");
    }

    @Test
    void sedationLimitsConsciousnessAndSlowsPainShock() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        give(m, DIAZEPAM, s);
        run(m, input(3), s, 120);
        assertTrue(m.consciousness <= 65, "сонливость: сознание не выше 100 − сила, " + m.consciousness);
    }

    @Test
    void antisepticLowersInfectionRiskAndOintmentCuresShallowInfection() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Wound fresh = new Wound(WoundType.CUT, 20);
        m.part(BodyPart.CHEST).wounds.add(fresh);
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG_TOPICAL, s, ANTISEPTIC));
        assertEquals(BodyPart.CHEST, Treatments.bestPart(m, TreatmentAction.DRUG_TOPICAL, s, ANTISEPTIC));
        give(m, ANTISEPTIC, s);
        assertEquals(s.antisepticInfectionFactor, fresh.infectionRisk, 1e-9);

        assertEquals("no_infection_here", Treatments.check(m, BodyPart.CHEST, TreatmentAction.DRUG_TOPICAL, s, OINTMENT));
        fresh.infectionStage = Wound.Infection.INFECTED;
        fresh.infection = 40;
        give(m, OINTMENT, s);
        assertFalse(fresh.isInfected(), "мазь вылечила неглубокую рану");

        Wound deep = new Wound(WoundType.GUNSHOT, 60);
        deep.infectionStage = Wound.Infection.INFECTED;
        m.part(BodyPart.ABDOMEN).wounds.add(deep);
        assertEquals("infection_too_deep", Treatments.check(m, BodyPart.ABDOMEN, TreatmentAction.DRUG_TOPICAL, s, OINTMENT));
    }

    @Test
    void negativeCoagulationIncreasesBleeding() {
        MedicalSettings s = settings();
        MedicalState a = new MedicalState(s);
        MedicalState b = new MedicalState(s);
        for (MedicalState m : new MedicalState[]{a, b}) m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.CUT, 40));
        b.addEffect(DrugEffect.COAGULATION, -0.3, 0, 3600);
        run(a, input(4), s, 60);
        run(b, input(4), s, 60);
        assertTrue(b.bloodVolume < a.bloodVolume - 10, "передозировка НПВС — кровит сильнее");
    }
}
