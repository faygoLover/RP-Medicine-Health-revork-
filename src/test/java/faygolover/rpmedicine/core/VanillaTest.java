package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап, п. 11 и 13: речь при одышке, «поднять» (тотем, ГМ), таймеры тошноты и глухоты. */
class VanillaTest {

    @Test
    void breathlessSpeechOnlyWithDyspnea() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertEquals(Speech.NORMAL, Speech.of(m, s));
        m.respRate = 30;
        assertEquals(Speech.BREATHLESS, Speech.of(m, s));
        assertTrue(Speech.BREATHLESS.canSpeak());
        double mid = Speech.breathlessness(m, s);
        m.spo2 = 70;
        assertTrue(Speech.breathlessness(m, s) > mid, "чем меньше кислорода, тем сильнее");
        m.down = MedicalState.Down.KNOCKDOWN;
        assertEquals(Speech.MUTED, Speech.of(m, s), "лежачий молчит, одышка не важна");
    }

    @Test
    void breathlessTextBreaksWords() {
        String text = "надо уходить отсюда быстро пока они не вернулись";
        String weak = Speech.breathless(text, 0.2, new SplittableRandom(1));
        String strong = Speech.breathless(text, 1.0, new SplittableRandom(1));
        assertEquals(text, weak.replace("...", ""), "слова те же, только обрывы");
        assertTrue(weak.contains("..."));
        assertEquals(text.split(" ").length - 1, strong.split("\\.\\.\\.").length - 1, "сильная одышка — по слову");
        assertEquals("да", Speech.breathless("да", 1.0, new SplittableRandom(1)), "одно слово не рвётся");
    }

    @Test
    void rescueRestoresLifeAndKeepsWounds() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.part(BodyPart.LEFT_LEG).wounds.add(new Wound(WoundType.CUT, 50));
        m.part(BodyPart.LEFT_LEG).arterial = true;
        m.bloodVolume = m.normalBlood(s) * 0.3;
        m.heart = MedicalState.Heart.ARREST;
        m.down = MedicalState.Down.CLINICAL;
        assertTrue(Physiology.rescue(m, s, 0.6));
        assertEquals(MedicalState.Down.NONE, m.down);
        assertEquals(MedicalState.Heart.NORMAL, m.heart);
        assertEquals(0.6, m.bloodFraction(s), 1e-9);
        assertFalse(m.part(BodyPart.LEFT_LEG).wounds.isEmpty(), "раны остаются");
        m.bloodVolume = m.normalBlood(s) * 0.9;
        Physiology.rescue(m, s, 0.6);
        assertEquals(0.9, m.bloodFraction(s), 1e-9, "кровь не уменьшается");
    }

    @Test
    void nauseaAndDeafnessWearOff() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.nauseaSeconds = 10;
        m.deafSeconds = 30;
        assertFalse(m.isQuiet(s));
        assertTrue(Examination.complaints(m, s).contains("nausea"));
        assertTrue(Examination.complaints(m, s).contains("deaf"));
        run(m, input(1), s, 31);
        assertEquals(0, m.nauseaSeconds);
        assertEquals(0, m.deafSeconds);
        assertFalse(Examination.complaints(m, s).contains("deaf"));
    }

    @Test
    void oxygenRaisesSpo2Ceiling() {
        MedicalSettings s = settings();
        MedicalState air = new MedicalState(s);
        MedicalState o2 = new MedicalState(s);
        air.pneumo = MedicalState.Pneumo.OPEN;
        o2.pneumo = MedicalState.Pneumo.OPEN;
        air.pneumoTimer = o2.pneumoTimer = 1e9;
        StepInput a = input(5);
        StepInput b = input(5);
        b.oxygen = true;
        run(air, a, s, 120);
        run(o2, b, s, 120);
        assertTrue(o2.spo2 > air.spo2 + 3, "кислород: SpO2 выше, " + o2.spo2 + " против " + air.spo2);
        assertTrue(o2.spo2 <= s.spo2Normal + 0.01, "не выше нормы");
    }
}
