package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Третий этап, п. 2: органы — урон, симптомы, заживление, диагностика. */
class OrgansTest {

    @Test
    void gunshotToAbdomenHitsOrganAndInfects() {
        MedicalSettings s = settings();
        s.gunshotOrganChance = 1.0;
        MedicalState m = new MedicalState(s);
        InjuryProfile p = new InjuryProfile("t", WoundType.GUNSHOT, InjuryProfile.Location.HIT_POINT);
        Injuries.Report r = Injuries.apply(m, p, 10, BodyPart.ABDOMEN, 0, PatientTraits.NONE, new Random(3), s);
        assertTrue(r.has(Injuries.Outcome.ORGAN));
        assertEquals(1, r.organs.size());
        Organ o = r.organs.get(0);
        assertEquals(BodyPart.ABDOMEN, o.part);
        assertTrue(m.organ(o) > 0);
        Wound w = m.part(BodyPart.ABDOMEN).wounds.get(0);
        assertTrue(w.isInfected(), "огнестрел в живот — брюшная полость заражена");
        // Порез в руку органы не трогает.
        MedicalState arm = new MedicalState(s);
        Injuries.apply(arm, p, 10, BodyPart.LEFT_ARM, 0, PatientTraits.NONE, new Random(3), s);
        assertEquals(0, Organs.worst(arm));
    }

    @Test
    void bluntChestTraumaBruisesOrgan() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        InjuryProfile p = new InjuryProfile("t", WoundType.BRUISE, InjuryProfile.Location.HIT_POINT);
        Injuries.apply(m, p, 5, BodyPart.CHEST, 0, PatientTraits.NONE, new Random(1), s);
        assertEquals(0, Organs.worst(m), "слабый удар органы не трогает");
        Injuries.apply(m, p, 20, BodyPart.CHEST, 0, PatientTraits.NONE, new Random(1), s);
        assertEquals(20 * s.bluntOrganFactor, m.organ(Organ.HEART) + m.organ(Organ.LUNGS), 1e-9, "ушиб сердца или лёгких");
    }

    @Test
    void organsHealBelowLimitOnly() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.setOrgan(Organ.LIVER, 20);
        m.setOrgan(Organ.HEART, 60);
        Healing.fastForward(m, 5 * 3600, s);
        assertEquals(20 - 5 * s.organHealPerHour, m.organ(Organ.LIVER), 0.01, "ниже 50 — заживает сам");
        assertEquals(60, m.organ(Organ.HEART), 1e-9, "с 50 — только операцией");
    }

    @Test
    void lungsLowerSpo2AndStopBreathing() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.setOrgan(Organ.LUNGS, 85);
        assertEquals(s.spo2Normal - s.lungsSpo2Penalty80, Physiology.spo2Ceiling(m, s), 1e-9);
        run(m, input(1), s, 60);
        assertTrue(m.spo2 < s.spo2Normal - 15, "SpO2 падает, " + m.spo2);
        assertTrue(Examination.complaints(m, s).contains("cough"));
        assertEquals("breath_crackles", Diagnostics.breathSound(m));
        m.setOrgan(Organ.LUNGS, 100);
        run(m, input(1), s, 2);
        assertTrue(m.respiratoryArrest, "лёгкие 100 % — дыхания нет");
    }

    @Test
    void destroyedHeartStopsAndCannotRestart() {
        MedicalSettings s = settings();
        MedicalState weak = new MedicalState(s);
        weak.setOrgan(Organ.HEART, 85);
        run(weak, input(2), s, 120);
        assertTrue(weak.pressure < s.normalPressure - 10, "сердечная недостаточность — давление ниже, " + weak.pressure);
        assertEquals("heart_uneven", Diagnostics.heartSound(weak));
        MedicalState dead = new MedicalState(s);
        dead.setOrgan(Organ.HEART, 100);
        run(dead, input(2), s, 1);
        assertEquals(MedicalState.Heart.ARREST, dead.heart);
        assertFalse(Physiology.canRestartHeart(dead, s));
    }

    @Test
    void liverMakesBleedingWorse() {
        MedicalSettings s = settings();
        MedicalState a = new MedicalState(s);
        MedicalState b = new MedicalState(s);
        for (MedicalState m : new MedicalState[]{a, b}) m.part(BodyPart.LEFT_LEG).wounds.add(new Wound(WoundType.CUT, 40));
        b.setOrgan(Organ.LIVER, 85);
        run(a, input(3), s, 60);
        run(b, input(3), s, 60);
        assertTrue(b.bloodVolume < a.bloodVolume - 5, "печень 85 % — кровит сильнее");
        assertTrue(Examination.complaints(b, s).contains("side_pain"));
    }

    @Test
    void kidneysAndMissingOrgansHurtBrain() {
        MedicalSettings s = settings();
        MedicalState k = new MedicalState(s);
        k.setOrgan(Organ.KIDNEYS, 100);
        run(k, input(4), s, 3600);
        assertEquals(100 - s.kidneyBrainPerHour100, k.brain, 0.5, "почки 100 % — мозг −5 % в час");
        MedicalState missing = new MedicalState(s);
        missing.organsMissing |= Organ.LIVER.bit();
        assertEquals(100, missing.organ(Organ.LIVER));
        assertFalse(missing.isQuiet(s));
        run(missing, input(4), s, 600);
        assertTrue(missing.brain < 70, "без печени мозг гибнет за полчаса, " + missing.brain);
    }

    @Test
    void sepsisDamagesOrgansAndPeritonitisFeedsSepsis() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.sepsis = 40;
        StepInput in = input(5);
        in.dt = 3600;
        Organs.tick(m, in, s);
        for (Organ o : Organ.VALUES) assertEquals(s.sepsisOrganPerHour, m.organ(o), 1e-9);
        MedicalState p = new MedicalState(s);
        assertEquals(0, Infections.sepsisSources(p));
        p.setOrgan(Organ.INTESTINES, 100);
        assertEquals(1, Infections.sepsisSources(p), "перитонит — источник сепсиса");
    }

    @Test
    void scannerAndLabShowOrgans() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertTrue(Diagnostics.scanPart(m, BodyPart.ABDOMEN).contains("scan_organs_ok"));
        m.setOrgan(Organ.LIVER, 75);
        m.setOrgan(Organ.KIDNEYS, 10);
        var words = Diagnostics.scanPart(m, BodyPart.ABDOMEN);
        assertTrue(words.contains("scan_liver_severe") && words.contains("scan_kidneys_bruise"), words.toString());
        assertFalse(Diagnostics.scanPart(m, BodyPart.LEFT_ARM).stream().anyMatch(w -> w.startsWith("scan_organs")), "на руке органов нет");
        Diagnostics.Lab lab = Diagnostics.lab(m, s);
        assertTrue(lab.alt() > 40 && lab.creatinine() <= 180 && lab.troponin() <= 14);
        var exam = Examination.examine(m, 5, false, s);
        assertFalse(exam.general().stream().anyMatch(l -> l.key().equals("jaundice")), "желтуха с 80");
        m.setOrgan(Organ.LIVER, 85);
        exam = Examination.examine(m, 5, false, s);
        assertTrue(exam.general().stream().anyMatch(l -> l.key().equals("jaundice")));
    }

    @Test
    void paracetamolOverdoseHurtsLiver() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.addEffect(DrugEffect.LIVER_TOXICITY, 30, 0, 3600);
        run(m, input(6), s, 3600);
        assertEquals(30 - s.organHealPerHour, m.organ(Organ.LIVER), 0.5, "яд 30 % за час минус заживление");
    }
}
