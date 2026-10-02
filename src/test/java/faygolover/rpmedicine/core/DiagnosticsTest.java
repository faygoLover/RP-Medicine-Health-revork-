package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап, п. 3: показания приборов. */
class DiagnosticsTest {

    @Test
    void stethoscopeHearsPneumothoraxCracklesAndFibrillation() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("breath_normal", Diagnostics.breathSound(m));
        assertEquals("heart_normal", Diagnostics.heartSound(m));
        m.pneumo = MedicalState.Pneumo.OPEN;
        assertEquals("breath_one_side_weak", Diagnostics.breathSound(m));
        m.pneumo = MedicalState.Pneumo.NONE;
        m.part(BodyPart.CHEST).internalBleed = 20;
        assertEquals("breath_crackles", Diagnostics.breathSound(m));
        m.heart = MedicalState.Heart.FIBRILLATION;
        assertEquals("heart_irregular", Diagnostics.heartSound(m));
        assertEquals("breath_none", Diagnostics.breathSound(m));
        Treatments.Result r = Treatments.apply(m, BodyPart.CHEST, TreatmentAction.STETHOSCOPE, false, new Random(1), s);
        assertFalse(r.consumed, "прибор не тратится");
        assertArrayEquals(new String[]{"breath_none", "heart_irregular"}, r.words);
        assertEquals("unclear", Treatments.apply(m, BodyPart.CHEST, TreatmentAction.STETHOSCOPE, true, new Random(1), s).key);
    }

    @Test
    void scannerFindsInternalBleedingForeignBodiesFracture() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        BodyPartState abd = m.part(BodyPart.ABDOMEN);
        abd.internalBleed = 30;
        abd.bullets = 2;
        var words = Diagnostics.scanPart(m, BodyPart.ABDOMEN);
        assertTrue(words.contains("scan_internal_bleeding"));
        assertTrue(words.contains("scan_bullets_2"));
        assertTrue(words.contains("scan_no_fracture"));
        m.part(BodyPart.LEFT_LEG).fracture = BodyPartState.Fracture.OPEN;
        assertTrue(Diagnostics.scanPart(m, BodyPart.LEFT_LEG).contains("scan_fracture_open"));
        assertTrue(Diagnostics.scanPart(m, BodyPart.LEFT_LEG).contains("scan_no_foreign"));
    }

    @Test
    void thermometerAndHemoanalyzer() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bodyTemp = 38.47;
        Treatments.Result r = Treatments.apply(m, BodyPart.CHEST, TreatmentAction.THERMOMETER, false, new Random(1), s);
        assertEquals(38.5, r.args[0], 1e-9, "температура с точностью 0,1");
        m.bloodType = BloodType.B_NEG;
        m.bloodVolume = m.normalBlood(s) * 0.7;
        m.sepsis = 20;
        Treatments.Result h = Treatments.apply(m, BodyPart.CHEST, TreatmentAction.HEMOANALYZER, false, new Random(1), s);
        assertArrayEquals(new String[]{"blood_b-", "hb_low", "infection_systemic"}, h.words);
    }

    @Test
    void labGivesNumbers() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodType = BloodType.O_POS;
        Diagnostics.Lab healthy = Diagnostics.lab(m, s);
        assertEquals(140, healthy.hemoglobin(), 1);
        assertTrue(healthy.leukocytes() < 9, "лейкоциты в норме");
        assertFalse(healthy.sepsis());
        Wound w = new Wound(WoundType.BURN, 20);
        w.infectionStage = Wound.Infection.INFECTED;
        w.infection = 80;
        m.part(BodyPart.LEFT_ARM).wounds.add(w);
        m.sepsis = 30;
        m.bloodVolume = m.normalBlood(s) * 0.6;
        Diagnostics.Lab sick = Diagnostics.lab(m, s);
        assertTrue(sick.hemoglobin() < 100, "кровопотеря — гемоглобин ниже");
        assertTrue(sick.leukocytes() > 9, "инфекция — лейкоциты выше");
        assertTrue(sick.sepsis());
    }
}
