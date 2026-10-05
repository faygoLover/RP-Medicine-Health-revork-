package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Вещества: толерантность, зависимость, ломка, опьянение (ТЗ третьего этапа, п. 9). */
class SubstancesTest {

    @Test
    void dosesBuildToleranceAndDependence() {
        MedicalSettings s = settings();
        s.dependenceFactor = 100; // зависимость наверняка
        MedicalState m = new MedicalState(s);
        Substances.dose(m, Substance.OPIOID, 1, PatientTraits.NONE, new SplittableRandom(1), s);
        assertEquals(Substance.OPIOID.toleranceGain, m.tolerance[Substance.OPIOID.ordinal()], 1e-9);
        assertTrue(Substances.dependent(m, Substance.OPIOID));
        assertEquals(1 - 0.5 * Substance.OPIOID.toleranceGain / 100.0, Substances.durationFactor(m, Substance.OPIOID), 1e-9, "морфин короче");
        // Курс доз: толерантность высокая. Ломка через 8 часов без дозы: боль.
        m.tolerance[Substance.OPIOID.ordinal()] = 60;
        run(m, input(1), s, Substance.OPIOID.withdrawalHours * 3600 + 60);
        assertTrue(Substances.withdrawal(m, Substance.OPIOID, s), "ломка");
        assertTrue(m.acutePain >= s.withdrawalPain - 1e-9, "боль при ломке");
        assertTrue(GameplayEffects.compute(m, PatientTraits.NONE, s).useTimeFactor >= s.withdrawalUseSlow - 1e-9, "руки медленнее");
        // Доза снимает ломку.
        Substances.dose(m, Substance.OPIOID, 1, PatientTraits.NONE, new SplittableRandom(2), s);
        assertFalse(Substances.withdrawal(m, Substance.OPIOID, s));
    }

    @Test
    void naloxonePrecipitatesWithdrawal() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.dependence |= Substance.OPIOID.bit();
        m.tolerance[Substance.OPIOID.ordinal()] = 50;
        Drug naloxone = new Drug("test:naloxone", Drug.Form.INJECTION, java.util.List.of(), 0, 0, java.util.List.of(), 0, false,
                Drug.Special.OPIOID_ANTIDOTE);
        Drugs.apply(m, BodyPart.CHEST, naloxone, false, new SplittableRandom(1), s);
        assertTrue(Substances.withdrawal(m, Substance.OPIOID, s), "налоксон — сразу ломка");
    }

    @Test
    void alcoholIntoxicatesTeetotalerTwice() {
        MedicalSettings s = settings();
        MedicalState a = new MedicalState(s);
        MedicalState b = new MedicalState(s);
        PatientTraits teetotaler = new PatientTraits(false, false, false, false, false, false, false, false, true, false);
        Substances.dose(a, Substance.ALCOHOL, 1, PatientTraits.NONE, new SplittableRandom(1), s);
        Substances.dose(b, Substance.ALCOHOL, 1, teetotaler, new SplittableRandom(1), s);
        assertEquals(2 * a.intoxication, b.intoxication, 1e-9, "непьющий пьянеет вдвое");
        a.intoxication = s.intoxicationPassOut + 1;
        run(a, input(1), s, 4);
        assertTrue(a.isDown(), "очень пьян — вырубило");
        assertTrue(Substances.slur("здравствуйте товарищи", 90, new SplittableRandom(3)).length() != "здравствуйте товарищи".length()
                || !Substances.slur("здравствуйте товарищи", 90, new SplittableRandom(3)).equals("здравствуйте товарищи"), "речь невнятная");
        double before = a.intoxication;
        run(a, input(2), s, 3600);
        assertTrue(a.intoxication < before - s.alcoholDecayPerHour * 0.9, "трезвеет");
    }

    @Test
    void perksStartWithToleranceAndDependence() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        StepInput in = input(1);
        in.traits = new PatientTraits(false, false, false, false, false, false, true, true, false, true);
        run(m, in, s, 1);
        assertTrue(m.tolerance[Substance.ALCOHOL.ordinal()] >= s.alcoholicTolerance - 0.01 && Substances.dependent(m, Substance.ALCOHOL));
        assertTrue(m.tolerance[Substance.NICOTINE.ordinal()] >= s.smokerNicotineTolerance - 0.01 && Substances.dependent(m, Substance.NICOTINE));
        assertTrue(Substances.dependent(m, Substance.CAFFEINE));
    }

    @Test
    void morphineCountsAsOpioid() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        Treatments.apply(m, BodyPart.CHEST, TreatmentAction.MORPHINE, false, new SplittableRandom(1), s);
        assertTrue(m.tolerance[Substance.OPIOID.ordinal()] > 0, "морфин даёт толерантность к опиатам");
    }
}
