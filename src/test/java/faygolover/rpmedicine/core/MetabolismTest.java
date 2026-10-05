package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Хронические состояния от перков (ТЗ третьего этапа, п. 8). */
class MetabolismTest {
    private static final PatientTraits DIABETIC = new PatientTraits(false, false, false, false, false, true, false, false);

    private static StepInput diabetic(long seed) {
        StepInput in = input(seed);
        in.traits = DIABETIC;
        return in;
    }

    @Test
    void foodRaisesInsulinLowers() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Metabolism.eat(m, 8, s);
        assertEquals(Metabolism.NORMAL_SUGAR + 8 * s.sugarPerNutrition, m.bloodSugar, 1e-9);
        double before = m.bloodSugar;
        m.addEffect(DrugEffect.INSULIN, 3, 0, 2 * 3600);
        run(m, diabetic(1), s, 2 * 3600);
        assertEquals(before - 6 - 2 * s.sugarDeclinePerHour, m.bloodSugar, 0.3, "инсулин −6 за 2 часа плюс своё снижение");
    }

    @Test
    void hypoglycemiaFaintsAndGlucoseHelps() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodSugar = 2.6;
        run(m, diabetic(1), s, 20);
        assertTrue(m.isDown(), "сахар ниже 2,8 — обморок");
        Drug glucose = new Drug("test:glucose", Drug.Form.PILL, java.util.List.of(), 0, 0, java.util.List.of(), 0, false, Drug.Special.GLUCOSE);
        Drugs.apply(m, BodyPart.CHEST, glucose, false, new SplittableRandom(1), s);
        assertTrue(m.bloodSugar > 5, "глюкоза поднимает сахар, стало " + m.bloodSugar);
    }

    @Test
    void nonDiabeticSugarStaysNormal() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.bloodSugar = 12;
        run(m, input(1), s, 3600);
        assertEquals(Metabolism.NORMAL_SUGAR, m.bloodSugar, 0.1, "без диабета сахар в норме");
    }

    @Test
    void smokerAndAlcoholicOrganFloors() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        StepInput in = input(1);
        in.traits = new PatientTraits(false, false, false, false, false, false, true, true);
        run(m, in, s, 3600);
        assertEquals(s.smokerLungsFloor, m.organ(Organ.LUNGS), 1e-9, "лёгкие курильщика");
        assertEquals(s.alcoholicLiverFloor, m.organ(Organ.LIVER), 1e-9, "печень алкоголика");
        assertEquals("glucometer", Treatments.apply(m, BodyPart.CHEST, TreatmentAction.GLUCOMETER, false, new SplittableRandom(1), s).key);
    }
}
