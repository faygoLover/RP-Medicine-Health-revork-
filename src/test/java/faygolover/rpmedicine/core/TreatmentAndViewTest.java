package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentAndViewTest {

    @Test
    void treatmentRefusedWhenNotNeeded() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.BRUISE, 10));
        assertEquals("nothing_to_dress", Treatments.check(m, BodyPart.LEFT_ARM, TreatmentAction.BANDAGE, s));
        assertEquals("tourniquet_not_needed", Treatments.check(m, BodyPart.LEFT_ARM, TreatmentAction.TOURNIQUET, s));
        assertEquals("no_fracture", Treatments.check(m, BodyPart.LEFT_ARM, TreatmentAction.SPLINT, s));
        assertEquals("splint_ribs", Treatments.check(m, BodyPart.CHEST, TreatmentAction.SPLINT, s));
        assertEquals("pulse_present", Treatments.check(m, BodyPart.CHEST, TreatmentAction.CPR, s));
        assertNull(Treatments.bestPart(m, TreatmentAction.BANDAGE, s));
    }

    @Test
    void quickUsePicksMostNeededPart() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).wounds.add(new Wound(WoundType.CUT, 5));
        m.part(BodyPart.RIGHT_LEG).wounds.add(new Wound(WoundType.CUT, 40));
        assertEquals(BodyPart.RIGHT_LEG, Treatments.bestPart(m, TreatmentAction.BANDAGE, s));
        m.part(BodyPart.LEFT_FOOT).arterial = true;
        assertEquals(BodyPart.LEFT_LEG, Treatments.bestPart(m, TreatmentAction.TOURNIQUET, s));
    }

    @Test
    void bandageStopsModerateBleedingAndErrorWeakens() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Wound w = new Wound(WoundType.CUT, 30); // 60 мл/мин — среднее
        m.part(BodyPart.LEFT_ARM).wounds.add(w);
        Treatments.apply(m, BodyPart.LEFT_ARM, TreatmentAction.BANDAGE, false, new Random(1), s);
        assertTrue(w.bleed(s) < 10);
        w.removeDressing();
        Treatments.apply(m, BodyPart.LEFT_ARM, TreatmentAction.BANDAGE, true, new Random(1), s);
        assertTrue(w.bleed(s) > 20, "плохая повязка держит хуже");
    }

    @Test
    void hemostaticStopsArterial() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState ps = m.part(BodyPart.HEAD);
        ps.arterial = true;
        ps.wounds.add(new Wound(WoundType.CUT, 30));
        assertNull(Treatments.check(m, BodyPart.HEAD, TreatmentAction.HEMOSTATIC, s));
        Treatments.apply(m, BodyPart.HEAD, TreatmentAction.HEMOSTATIC, false, new Random(1), s);
        assertFalse(ps.arterial);
        assertFalse(ps.wounds.get(0).isFreshDressing(s), "место после гемостатика не рвётся повторно");
    }

    @Test
    void skillSpeedAndErrors() {
        MedicalSettings s = settings();
        assertEquals(0.6, Skill.speed(0, s), 1e-9);
        assertEquals(1.2, Skill.speed(3, s), 1e-9);
        assertEquals(0.25, Skill.errorChance(0, 0, s), 1e-9);
        assertEquals(0.02, Skill.errorChance(3, 3, s), 1e-9);
        assertTrue(Skill.errorChance(0, 3, s) > 0.5, "ниже минимального уровня — высокий шанс ошибки");
        assertEquals(4 / 0.6 * 1.5, Skill.applySeconds(4, 0, true, 1, s), 1e-9);
    }

    @Test
    void examinationDetailDependsOnLevel() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.CHEST).wounds.add(new Wound(WoundType.GUNSHOT, 30));
        m.part(BodyPart.CHEST).internalBleed = 50;
        m.part(BodyPart.CHEST).bullets = 1;
        var v0 = Examination.examine(m, 0, false, s);
        var chest0 = v0.parts().get(BodyPart.CHEST.ordinal()).lines();
        assertTrue(chest0.stream().anyMatch(l -> l.key().equals("wound")));
        assertTrue(chest0.stream().noneMatch(l -> l.key().startsWith("wound_type")), "уровень 0 не видит тип раны");
        assertTrue(chest0.stream().noneMatch(l -> l.key().equals("suspect_internal")));

        var v2 = Examination.examine(m, 2, false, s).parts().get(BodyPart.CHEST.ordinal()).lines();
        assertTrue(v2.stream().anyMatch(l -> l.key().equals("suspect_internal")));
        assertTrue(v2.stream().noneMatch(l -> l.key().equals("bullet_inside")));

        var v3 = Examination.examine(m, 3, false, s).parts().get(BodyPart.CHEST.ordinal()).lines();
        assertTrue(v3.stream().anyMatch(l -> l.key().equals("bullet_inside")));

        var self = Examination.examine(m, 8, true, s);
        assertEquals(s.selfViewMaxLevel, self.level(), "себя осматривать труднее");
    }

    @Test
    void brokenLegLimpsAndPainkillerMasksIt() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).fracture = BodyPartState.Fracture.CLOSED;
        var mods = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertEquals(0.6, mods.speed, 1e-9);
        assertTrue(mods.noSprint && mods.noJump);
        m.part(BodyPart.RIGHT_LEG).fracture = BodyPartState.Fracture.CLOSED;
        assertTrue(GameplayEffects.compute(m, PatientTraits.NONE, s).crawl, "обе ноги сломаны — только ползком");
        m.morphineSeconds = 600;
        var masked = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertFalse(masked.crawl);
        assertFalse(masked.noSprint, "обезболивание снимает ограничения перелома");
    }

    @Test
    void bothArmsBrokenDisableShootingAndTreating() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_ARM).fracture = BodyPartState.Fracture.CLOSED;
        m.part(BodyPart.RIGHT_ARM).fracture = BodyPartState.Fracture.CLOSED;
        assertTrue(GameplayEffects.compute(m, PatientTraits.NONE, s).armsDisabled);
    }

    @Test
    void perksChangeShockThreshold() {
        MedicalSettings s = settings();
        assertEquals(95, new PatientTraits(true, false, false, false, false).shockThreshold(s), 1e-9);
        assertEquals(75, new PatientTraits(false, false, false, true, false).shockThreshold(s), 1e-9);
    }
}
