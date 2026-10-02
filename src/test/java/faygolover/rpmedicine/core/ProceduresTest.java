package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап, п. 7–8: вывихи, пинцет, швы. */
class ProceduresTest {

    private static Treatments.Result apply(MedicalState m, BodyPart p, TreatmentAction a, boolean error, MedicalSettings s, Treatments.Extra extra) {
        return Treatments.apply(m, p, a, error, new Random(3), s, extra);
    }

    @Test
    void bluntHitsCanDislocateLimbs() {
        MedicalSettings s = settings();
        s.dislocationChancePerSeverity = 1; // всегда
        InjuryProfile blunt = new InjuryProfile("test/blunt", WoundType.BRUISE, InjuryProfile.Location.HIT_POINT);
        MedicalState m = new MedicalState(s);
        Injuries.Report r = Injuries.apply(m, blunt, 4, BodyPart.LEFT_LEG, 0, PatientTraits.NONE, new Random(1), s);
        assertTrue(r.has(Injuries.Outcome.DISLOCATION) || m.part(BodyPart.LEFT_LEG).hasFracture(), "вывих (или перелом)");
        MedicalState chest = new MedicalState(s);
        Injuries.apply(chest, blunt, 4, BodyPart.CHEST, 0, PatientTraits.NONE, new Random(1), s);
        assertFalse(chest.part(BodyPart.CHEST).dislocated, "в груди вывихов нет");
    }

    @Test
    void dislocationHurtsAndLimpsWeakerThanFracture() {
        MedicalSettings s = settings();
        MedicalState disl = new MedicalState(s);
        disl.part(BodyPart.LEFT_LEG).dislocated = true;
        MedicalState frac = new MedicalState(s);
        frac.part(BodyPart.LEFT_LEG).fracture = BodyPartState.Fracture.CLOSED;
        run(disl, input(1), s, 5);
        run(frac, input(1), s, 5);
        assertTrue(disl.pain >= 25, "вывих болит");
        var md = GameplayEffects.compute(disl, PatientTraits.NONE, s);
        var mf = GameplayEffects.compute(frac, PatientTraits.NONE, s);
        assertTrue(md.speed < 1 && md.speed > mf.speed, "хромает, но меньше, чем с переломом");
        assertTrue(md.noSprint);
        healing(disl, s);
        assertTrue(disl.part(BodyPart.LEFT_LEG).dislocated, "сам не проходит");
    }

    private static void healing(MedicalState m, MedicalSettings s) {
        Healing.fastForward(m, 24 * 3600, s);
    }

    @Test
    void reductionWithoutAnalgesiaHurtsAndErrorMayBreakBone() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.RIGHT_ARM).dislocated = true;
        assertNull(Treatments.check(m, BodyPart.RIGHT_ARM, TreatmentAction.REDUCE, s));
        assertEquals("reduced", apply(m, BodyPart.RIGHT_ARM, TreatmentAction.REDUCE, false, s, null).key);
        assertFalse(m.part(BodyPart.RIGHT_ARM).dislocated);
        assertEquals(s.reductionPain, m.acutePain, "без обезболивания — резкая боль");
        run(m, input(2), s, s.reductionPainSeconds + 1);
        assertEquals(0, m.acutePain, "прошла через 10 с");

        MedicalState calm = new MedicalState(s);
        calm.part(BodyPart.RIGHT_ARM).dislocated = true;
        calm.morphineSeconds = 600;
        apply(calm, BodyPart.RIGHT_ARM, TreatmentAction.REDUCE, false, s, null);
        assertEquals(0, calm.acutePainSeconds, "под морфином не больно");

        s.reductionFractureChance = 1;
        MedicalState bad = new MedicalState(s);
        bad.part(BodyPart.LEFT_LEG).dislocated = true;
        assertEquals("reduction_fracture", apply(bad, BodyPart.LEFT_LEG, TreatmentAction.REDUCE, true, s, null).key);
        assertTrue(bad.part(BodyPart.LEFT_LEG).hasFracture(), "ошибка — перелом");
    }

    @Test
    void tweezersRemoveBulletAndNonSterileRaisesInfectionRisk() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState abd = m.part(BodyPart.ABDOMEN);
        abd.bullets = 2;
        Wound w = new Wound(WoundType.GUNSHOT, 30);
        abd.wounds.add(w);
        assertEquals("no_foreign_body", Treatments.check(m, BodyPart.LEFT_ARM, TreatmentAction.TWEEZERS, s));
        assertEquals(BodyPart.ABDOMEN, Treatments.bestPart(m, TreatmentAction.TWEEZERS, s));
        assertEquals("bullet_removed", apply(m, BodyPart.ABDOMEN, TreatmentAction.TWEEZERS, false, s, new Treatments.Instrument(true)).key);
        assertEquals(1, abd.bullets);
        assertEquals(1.0, w.infectionRisk, 1e-9, "стерильный пинцет риск не меняет");
        apply(m, BodyPart.ABDOMEN, TreatmentAction.TWEEZERS, false, s, new Treatments.Instrument(false));
        assertEquals(0, abd.bullets);
        assertEquals(s.nonSterileInfectionFactor, w.infectionRisk, 1e-9, "нестерильный — ×1,5");
    }

    @Test
    void tweezersErrorBleedsAndPainCausesShockWithoutAnalgesia() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.RIGHT_LEG).fragments = 1;
        double before = m.part(BodyPart.RIGHT_LEG).totalSeverity();
        assertEquals("extraction_failed", apply(m, BodyPart.RIGHT_LEG, TreatmentAction.TWEEZERS, true, s, null).key);
        assertEquals(1, m.part(BodyPart.RIGHT_LEG).fragments, "осколок на месте");
        assertTrue(m.part(BodyPart.RIGHT_LEG).totalSeverity() > before, "задели стенку — новая рана");
        double t = runUntil(m, input(3), s, 60, st -> st.painShock);
        assertTrue(t > 0, "без обезболивания — болевой шок");
    }

    @Test
    void suturesStopBleedingAndSpeedHealing() {
        MedicalSettings s = settings();
        MedicalState plain = new MedicalState(s);
        MedicalState sewn = new MedicalState(s);
        for (MedicalState m : new MedicalState[]{plain, sewn}) {
            Wound w = new Wound(WoundType.CUT, 40);
            w.dressing = Dressing.BANDAGE;
            m.part(BodyPart.LEFT_ARM).wounds.add(w);
        }
        assertEquals("sutured", apply(sewn, BodyPart.LEFT_ARM, TreatmentAction.SUTURE, false, s, null).key);
        assertEquals(0, sewn.totalExternalBleed(s), 1e-9, "швы — кровотечение 0");
        double tPlain = Healing.woundHealSeconds(plain.part(BodyPart.LEFT_ARM).wounds.get(0), plain.part(BodyPart.LEFT_ARM), s);
        double tSewn = Healing.woundHealSeconds(sewn.part(BodyPart.LEFT_ARM).wounds.get(0), sewn.part(BodyPart.LEFT_ARM), s);
        assertEquals(s.sutureHealFactor, tPlain / tSewn, 1e-6, "заживление ×2");
        assertEquals("nothing_to_suture", Treatments.check(sewn, BodyPart.LEFT_ARM, TreatmentAction.SUTURE, s));

        MedicalState weak = new MedicalState(s);
        weak.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.CUT, 40));
        double raw = weak.totalExternalBleed(s);
        apply(weak, BodyPart.LEFT_ARM, TreatmentAction.SUTURE, true, s, null);
        assertEquals(raw * s.weakSutureBleedFactor, weak.totalExternalBleed(s), 1e-6, "слабый шов кровит");

        assertNull(Treatments.check(sewn, BodyPart.LEFT_ARM, TreatmentAction.SCISSORS, s));
        apply(sewn, BodyPart.LEFT_ARM, TreatmentAction.SCISSORS, false, s, null);
        assertFalse(sewn.part(BodyPart.LEFT_ARM).wounds.get(0).sutured, "швы сняты");
    }

    @Test
    void bulletMustBeRemovedBeforeSuturing() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.CHEST).wounds.add(new Wound(WoundType.GUNSHOT, 20));
        m.part(BodyPart.CHEST).bullets = 1;
        assertEquals("foreign_body_first", Treatments.check(m, BodyPart.CHEST, TreatmentAction.SUTURE, s));
    }
}
