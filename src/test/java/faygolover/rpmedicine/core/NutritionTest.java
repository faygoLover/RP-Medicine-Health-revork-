package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.run;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Питание: запасы, трата, нехватка и баланс. */
class NutritionTest {

    @Test
    void eatingFillsAndTimeDrains() {
        MedicalSettings s = settings();
        s.nutritionEnabled = true;
        MedicalState m = new MedicalState(s);
        java.util.Arrays.fill(m.nutrients, 0);
        Nutrition.Food steak = new Nutrition.Food(0, 52, 30, 0, 2).withKcal();
        assertEquals(52 * 4 + 30 * 9, steak.kcal(), 1e-9, "калории по БЖУ");
        Nutrition.eat(m, steak, s);
        assertEquals(52 * s.proteinPerGram, m.nutrients[Nutrition.PROTEIN], 1e-9);
        assertEquals(30 * s.fatPerGram, m.nutrients[Nutrition.FAT], 1e-9);
        double before = m.nutrients[Nutrition.PROTEIN];
        run(m, input(1), s, 3600);
        assertEquals(before - s.proteinDecayPerHour, m.nutrients[Nutrition.PROTEIN], 0.5, "белок тратится за час");
    }

    @Test
    void deficiencyAndBalanceAffectBody() {
        MedicalSettings s = settings();
        s.nutritionEnabled = true;
        MedicalState m = new MedicalState(s);
        java.util.Arrays.fill(m.nutrients, 60);
        StepInput in = input(1);
        Nutrition.tick(m, in, s);
        assertEquals(s.balancedHealFactor, in.healFactor, 1e-9, "баланс — заживление лучше");
        m.nutrients[Nutrition.PROTEIN] = 5;
        m.nutrients[Nutrition.CARBS] = 5;
        StepInput in2 = input(2);
        Nutrition.tick(m, in2, s);
        assertEquals(s.proteinLowHealFactor, in2.healFactor, 1e-9, "мало белка — заживление хуже");
        GameplayEffects.Mods mods = GameplayEffects.compute(m, PatientTraits.NONE, s);
        assertTrue(mods.staminaCap <= s.carbsLowStaminaCap + 1e-9, "мало углеводов — быстро устаёт");
        assertTrue(Nutrition.anyLow(m, s));
    }
}
