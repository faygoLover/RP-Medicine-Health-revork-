package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Правки по итогам проверки в игре 04–05.10.2026 (docs/test_review.md, решения п. 1.13). */
class PlaytestFixesTest {

    private static InjuryProfile profile(WoundType t) {
        return new InjuryProfile("t", t, InjuryProfile.Location.HIT_POINT);
    }

    @Test
    void damageToDestroyedLimbOverflowsToBody() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.CUT, 100));
        assertEquals(0, m.part(BodyPart.LEFT_ARM).integrity(), 1e-9);
        Injuries.hitPart(m, BodyPart.LEFT_ARM, 8, profile(WoundType.BRUISE), PatientTraits.NONE, new Random(3), s);
        double rest = 0;
        for (BodyPartState ps : m.parts) if (ps.part != BodyPart.LEFT_ARM) rest += ps.totalSeverity();
        assertEquals(8 * s.severityPerDamage * s.overflowArm, rest, 1e-6, "излишек с руки ×0,49 ушёл на остальное тело");
    }

    @Test
    void destroyedHeadKnocksDown() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.HEAD).wounds.add(new Wound(WoundType.GUNSHOT, 100));
        run(m, input(1), s, 2);
        assertTrue(m.isDown(), "разрушенная голова — лежит");
        assertEquals(MedicalState.Down.KNOCKDOWN, m.down, "открытая рана головы — угроза жизни, нокдаун");
    }

    @Test
    void destroyedHeadOverflowHitsBrain() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.HEAD).wounds.add(new Wound(WoundType.GUNSHOT, 100));
        double before = m.brain;
        Injuries.hitPart(m, BodyPart.HEAD, 10, profile(WoundType.GUNSHOT), PatientTraits.NONE, new Random(2), s);
        assertTrue(m.brain < before, "пуля в разрушенную голову бьёт по мозгу");
    }

    @Test
    void soreLegLimpsWithoutFracture() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).wounds.add(new Wound(WoundType.GUNSHOT, 30));
        Physiology.step(m, input(1), s);
        GameplayEffects.Mods mods = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertTrue(mods.noSprint, "огнестрел ноги — бега нет");
        assertTrue(mods.speed < 1.0, "хромота");
    }

    @Test
    void naturalAdrenalineDoesNotMaskFractures() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.RIGHT_ARM).fracture = BodyPartState.Fracture.CLOSED;
        m.part(BodyPart.LEFT_ARM).fracture = BodyPartState.Fracture.CLOSED;
        m.adrenalineSeconds = 60;
        GameplayEffects.Mods mods = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertTrue(mods.armsDisabled, "свой адреналин после ранения не снимает ограничения двух сломанных рук");
        m.adrenalineInjectionSeconds = 60;
        mods = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertFalse(mods.armsDisabled, "укол адреналина — снимает");
        assertTrue(mods.useTimeFactor > 1.0, "но руки всё равно работают хуже");
    }

    @Test
    void weakPillsDoNotMaskFractures() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.RIGHT_LEG).fracture = BodyPartState.Fracture.CLOSED;
        m.painkillerSeconds = 600;
        m.painkillerDelay = 0;
        assertTrue(GameplayEffects.compute(m, PatientTraits.NONE, s).noJump, "таблетки не дают прыгать на сломанной ноге");
    }

    @Test
    void forcedNeedleCausesPneumothorax() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("no_tension", Treatments.check(m, BodyPart.CHEST, TreatmentAction.NEEDLE, s));
        assertTrue(Treatments.FORCEABLE.contains("no_tension"));
        Treatments.applyForced(m, BodyPart.CHEST, TreatmentAction.NEEDLE, new Random(1), s, null);
        assertEquals(MedicalState.Pneumo.OPEN, m.pneumo, "игла в здоровую грудь — пневмоторакс");
    }

    @Test
    void tourniquetAllowedOverHemostaticWhenStillBleeding() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Wound w = new Wound(WoundType.GUNSHOT, 60);
        w.dressing = Dressing.HEMOSTATIC;
        w.dressingQuality = 0.4;
        m.part(BodyPart.RIGHT_LEG).wounds.add(w);
        assertNull(Treatments.check(m, BodyPart.RIGHT_LEG, TreatmentAction.TOURNIQUET, s), "плохо наложенный гемостатик — жгут можно");
    }

    @Test
    void stabilizationSlowsKnockdownOncePerKnockdown() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.KNOCKDOWN;
        double before = Physiology.knockdownBrainRate(m, s, PatientTraits.NONE);
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.STABILIZE, s));
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.STABILIZE, false, new Random(1), s);
        assertEquals(before * s.stabilizationFactor, Physiology.knockdownBrainRate(m, s, PatientTraits.NONE), 1e-9);
        assertEquals("stabilization_used", Treatments.check(m, BodyPart.CHEST, TreatmentAction.STABILIZE, s));
    }

    @Test
    void airwayFitsAnyUnconsciousPatient() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.FAINT;
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.AIRWAY, s), "обморок и наркоз — воздуховод можно");
    }
}
