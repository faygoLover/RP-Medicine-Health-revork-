package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import static faygolover.rpmedicine.core.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Второй этап, п. 12: голод, жажда, температура среды и их связь с медициной. */
class SurvivalTest {

    @Test
    void dehydrationLowersPressure() {
        MedicalSettings s = settings();
        MedicalState wet = new MedicalState(s);
        MedicalState dry = new MedicalState(s);
        // Здоровый компенсирует и обезвоживание, и потерю 15 % крови; вместе — уже нет.
        wet.bloodVolume *= 0.85;
        dry.bloodVolume *= 0.85;
        StepInput a = input(1);
        StepInput b = input(1);
        b.hydration = 0;
        run(wet, a, s, 120);
        run(dry, b, s, 120);
        assertTrue(dry.pressure < wet.pressure - 5, "обезвоживание снижает давление: " + dry.pressure + " против " + wet.pressure);
        assertEquals(wet.bloodVolume, dry.bloodVolume, 1e-6, "сама кровь не теряется");
        MedicalState healthy = new MedicalState(s);
        run(healthy, b, s, 120);
        assertEquals(s.normalPressure, healthy.pressure, 1, "одно обезвоживание здоровый переносит");
        assertEquals(0, Physiology.dehydration(a, s));
        assertEquals(1, Physiology.dehydration(b, s), 1e-9);
    }

    @Test
    void hungerSlowsHealing() {
        MedicalSettings s = settings();
        MedicalState fed = new MedicalState(s);
        MedicalState hungry = new MedicalState(s);
        Wound w1 = new Wound(WoundType.BRUISE, 40);
        Wound w2 = new Wound(WoundType.BRUISE, 40);
        fed.part(BodyPart.LEFT_LEG).wounds.add(w1);
        hungry.part(BodyPart.LEFT_LEG).wounds.add(w2);
        StepInput a = input(2);
        StepInput b = input(2);
        b.satiety = 0.1;
        run(fed, a, s, 300);
        run(hungry, b, s, 300);
        double healedFed = 40 - w1.severity;
        double healedHungry = 40 - w2.severity;
        assertTrue(healedFed > 1 && w1.severity > 1, "синяк заживает, но ещё не зажил: " + w1.severity);
        assertEquals(healedFed * s.hungerHealFactor, healedHungry, healedFed * 0.05, "голодный заживает медленнее во столько раз");
    }

    @Test
    void hungerAndThirstWeakenImmunity() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        StepInput ok = input(3);
        StepInput thirsty = input(3);
        thirsty.hydration = 0.1;
        StepInput hungry = input(3);
        hungry.satiety = 0.1;
        double base = Infections.immunityFactor(m, ok, s);
        assertEquals(base * s.immunityHungerFactor, Infections.immunityFactor(m, thirsty, s), 1e-9);
        assertEquals(base * s.immunityHungerFactor, Infections.immunityFactor(m, hungry, s), 1e-9);
    }

    @Test
    void coldLowersTemperatureHeartRateAndConsciousness() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        StepInput in = input(4);
        in.ambientTempShift = -4;
        run(m, in, s, 3600);
        assertEquals(s.normalBodyTemp - 4, m.bodyTemp, 0.05, "температура дошла до сдвига среды");
        assertTrue(m.heartRate < s.normalHeartRate - 5, "переохлаждение: пульс реже, " + m.heartRate);
        assertTrue(m.consciousness < 90, "переохлаждение туманит сознание, " + m.consciousness);
        assertTrue(Examination.complaints(m, s).contains("freezing"));
        // Согрелся — всё возвращается.
        in.ambientTempShift = 0;
        run(m, in, s, 3600);
        assertEquals(s.normalBodyTemp, m.bodyTemp, 0.01);
        assertFalse(Examination.complaints(m, s).contains("freezing"));
    }

    @Test
    void lowThirstIsNotQuietAndComplains() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        assertTrue(m.isQuiet(s));
        assertFalse(Examination.complaints(m, s).contains("thirsty"));
        m.thirst = 20;
        assertFalse(m.isQuiet(s), "обезвоженного надо считать");
        assertTrue(Examination.complaints(m, s).contains("thirsty"));
        m.reset(s);
        assertEquals(100, m.thirst);
    }
}
