package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Третий этап, п. 3: местная анестезия, наркоз, интубация, аппарат на столе. */
class AnesthesiaTest {

    private static Drug.Dose dose(DrugEffect e, double strength, double delay, double seconds) {
        return new Drug.Dose(e, strength, delay, seconds);
    }

    private static final Drug LIDOCAINE = new Drug("test:lidocaine", Drug.Form.INJECTION,
            List.of(dose(DrugEffect.LOCAL_ANESTHESIA, 1, 0, 1200)), 0, 0, List.of(), 0, false, Drug.Special.NONE);
    private static final Drug PROPOFOL = new Drug("test:propofol", Drug.Form.INJECTION,
            List.of(dose(DrugEffect.ANESTHESIA, 1, 0, 600), dose(DrugEffect.RESP_DEPRESSION, 0.6, 0, 600)), 0, 0, List.of(), 0, false,
            Drug.Special.NONE);
    private static final Drug KETAMINE = new Drug("test:ketamine", Drug.Form.INJECTION,
            List.of(dose(DrugEffect.ANESTHESIA, 1, 0, 300), dose(DrugEffect.PRESSURE, 10, 0, 300)), 0, 0, List.of(), 0, false,
            Drug.Special.NONE);

    private static void give(MedicalState m, BodyPart part, Drug d, MedicalSettings s) {
        Treatments.apply(m, part, TreatmentAction.DRUG, false, new Random(1), s, d);
    }

    @Test
    void lidocaineNumbsOnlyThatPart() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).wounds.add(new Wound(WoundType.CUT, 60));
        m.part(BodyPart.RIGHT_ARM).wounds.add(new Wound(WoundType.CUT, 30));
        assertEquals(BodyPart.LEFT_LEG, Treatments.bestPart(m, TreatmentAction.DRUG, s, LIDOCAINE), "в самую больную часть");
        double legBefore = Physiology.partPain(m, m.part(BodyPart.LEFT_LEG), s);
        double armBefore = Physiology.partPain(m, m.part(BodyPart.RIGHT_ARM), s);
        give(m, BodyPart.LEFT_LEG, LIDOCAINE, s);
        assertEquals(legBefore * s.localAnesthesiaPainFactor, Physiology.partPain(m, m.part(BodyPart.LEFT_LEG), s), 1e-9);
        assertEquals(armBefore, Physiology.partPain(m, m.part(BodyPart.RIGHT_ARM), s), 1e-9, "рука болит как раньше");
        assertFalse(m.hasEffect(DrugEffect.LOCAL_ANESTHESIA), "не общий эффект");
        run(m, input(1), s, 1201);
        assertEquals(0, m.part(BodyPart.LEFT_LEG).localAnesthesiaSeconds);
    }

    @Test
    void propofolPutsUnderWithoutPainAndDepressesBreathing() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).wounds.add(new Wound(WoundType.CUT, 50));
        give(m, BodyPart.CHEST, PROPOFOL, s);
        run(m, input(2), s, 5);
        assertEquals(MedicalState.Down.FAINT, m.down, "под наркозом лежит");
        assertEquals(0, m.pain, "боль не чувствуется");
        assertEquals(0, m.shockAccum, "болевой шок не копится");
        assertEquals("under_anesthesia", Treatments.check(m, BodyPart.CHEST, TreatmentAction.AMMONIA, s), "нашатырь не будит");
        run(m, input(2), s, 120);
        assertTrue(m.spo2 < 90, "дыхание угнетено — SpO2 падает, " + m.spo2);
    }

    @Test
    void intubationOnTableKeepsOxygen() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals("not_unconscious", Treatments.check(m, BodyPart.CHEST, TreatmentAction.INTUBATE, s));
        give(m, BodyPart.CHEST, PROPOFOL, s);
        run(m, input(3), s, 5);
        assertNull(Treatments.check(m, BodyPart.CHEST, TreatmentAction.INTUBATE, s));
        assertEquals("intubated", Treatments.apply(m, BodyPart.CHEST, TreatmentAction.INTUBATE, false, new Random(1), s).key);
        assertEquals("intubated_already", Treatments.check(m, BodyPart.CHEST, TreatmentAction.INTUBATE, s));
        StepInput table = input(3);
        table.ventilator = true;
        run(m, table, s, 300);
        assertTrue(m.spo2 >= s.spo2Normal - 2, "на столе дышит аппарат, " + m.spo2);
        // Наркоз прошёл — проснулся, трубку вынули.
        run(m, table, s, 400);
        assertEquals(MedicalState.Down.NONE, m.down);
        assertFalse(m.intubated, "в сознании трубку вынимают");
    }

    @Test
    void ketamineKeepsBreathing() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        give(m, BodyPart.CHEST, KETAMINE, s);
        run(m, input(4), s, 120);
        assertEquals(MedicalState.Down.FAINT, m.down);
        assertTrue(m.spo2 >= 90, "кетамин дыхание не угнетает, " + m.spo2);
        run(m, input(4), s, 200);
        assertEquals(MedicalState.Down.NONE, m.down, "через 5 минут проснулся");
    }

    @Test
    void failedIntubationWastesTube() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.down = MedicalState.Down.KNOCKDOWN;
        double spo2 = m.spo2;
        assertEquals("intubation_failed", Treatments.apply(m, BodyPart.CHEST, TreatmentAction.INTUBATE, true, new Random(1), s).key);
        assertFalse(m.intubated);
        assertTrue(m.spo2 < spo2);
    }
}
