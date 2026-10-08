package faygolover.rpmedicine.core;

import org.junit.jupiter.api.Test;

import static faygolover.rpmedicine.core.TestUtil.input;
import static faygolover.rpmedicine.core.TestUtil.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Питание глазами врача: запасы ведёт RP Culinary, здесь — как нехватка и баланс действуют на тело. */
class NutritionTest {

    @Test
    void withoutNutritionModNothingHappens() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        java.util.Arrays.fill(m.nutrients, 5);
        StepInput in = input(1);
        Nutrition.tick(m, in, s);
        assertEquals(1.0, in.healFactor, 1e-9, "мода питания нет — запасы неизвестны, действий нет");
        assertFalse(Nutrition.anyLow(m, s));
        assertFalse(Nutrition.balanced(m, s));
    }

    @Test
    void deficiencyAndBalanceAffectBody() {
        MedicalSettings s = settings();
        s.nutritionEnabled = true;
        MedicalState m = new MedicalState(s);
        m.nutritionKnown = true;
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
        m.poorAppetite = true;
        assertTrue(Nutrition.poorAppetite(m, s), "плохой аппетит — от мода питания");
    }

    @Test
    void labShowsNutritionNumbers() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        java.util.Arrays.fill(m.nutrients, 60);
        Diagnostics.Lab ok = Diagnostics.lab(m, s);
        assertTrue(ok.albumin() >= 35 && ok.albumin() <= 50 && ok.triglycerides() <= 1.7 && ok.glucose() >= 3.9 && ok.b12() >= 200, "сбалансировано — норма: " + ok);
        m.nutrients[Nutrition.PROTEIN] = 5;
        m.nutrients[Nutrition.VITAMINS] = 5;
        Diagnostics.Lab low = Diagnostics.lab(m, s);
        assertTrue(low.albumin() < 35 && low.b12() < 200, "нехватка белка и витаминов видна анализом");
    }

    @Test
    void faintByCallWakesByItself() {
        MedicalSettings s = settings();
        MedicalState m = new MedicalState(s);
        m.faintSeconds = 15;
        assertTrue(Physiology.rawConsciousness(m, s) <= 10, "голодный обморок — без сознания");
        m.faintSeconds = 0;
        assertEquals(100, Physiology.rawConsciousness(m, s), 1e-9, "прошёл — в сознании");
    }
}
