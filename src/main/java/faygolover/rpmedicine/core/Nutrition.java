package faygolover.rpmedicine.core;

/**
 * Питание глазами врача. Само питание (еда, калории, нутриенты, вода, вес) с версии 0.3.0 ведёт RP Culinary; перед
 * каждым шагом физиологии Medicine копирует его запасы в {@link MedicalState#nutrients} (через RP Core,
 * {@code integration/CoreNutrition}). Здесь — как нехватка и избыток действуют на тело: заживление, иммунитет,
 * холод, выносливость, удар. Без RP Culinary запасы неизвестны — и действий нет.
 */
public final class Nutrition {
    private Nutrition() {}

    public static final int PROTEIN = 0, FAT = 1, CARBS = 2, VITAMINS = 3, COUNT = 4;
    /** Имена нутриентов — те же, что в {@code NutritionProvider} RP Core. */
    public static final String[] IDS = {"protein", "fat", "carbs", "vitamins"};

    /** Плохой аппетит (сильно приелось, после капельницы) — по данным мода питания. */
    public static boolean poorAppetite(MedicalState m, MedicalSettings s) {
        return s.nutritionEnabled && m.nutritionKnown && m.poorAppetite;
    }

    /** Условия шага: нехватка и баланс — заживление, иммунитет, холод. */
    public static void tick(MedicalState m, StepInput in, MedicalSettings s) {
        if (!s.nutritionEnabled || !m.nutritionKnown || !in.online) return;
        if (low(m, PROTEIN, s)) in.healFactor *= s.proteinLowHealFactor;
        if (low(m, VITAMINS, s)) {
            in.immunityFactor *= s.vitaminsLowImmunityFactor;
            in.healFactor *= s.vitaminsLowHealFactor;
        }
        if (low(m, FAT, s)) in.ambientTempShift -= s.fatLowColdShift;
        if (balanced(m, s)) {
            in.healFactor *= s.balancedHealFactor;
            in.immunityFactor *= s.balancedImmunityFactor;
        }
    }

    public static boolean low(MedicalState m, int i, MedicalSettings s) {
        return s.nutritionEnabled && m.nutritionKnown && m.nutrients[i] < s.nutrientLow;
    }

    public static boolean high(MedicalState m, int i, MedicalSettings s) {
        return s.nutritionEnabled && m.nutritionKnown && m.nutrients[i] > s.nutrientHigh;
    }

    public static boolean balanced(MedicalState m, MedicalSettings s) {
        if (!s.nutritionEnabled || !m.nutritionKnown) return false;
        for (double v : m.nutrients) if (v < s.balancedMin || v > s.balancedMax) return false;
        return true;
    }

    public static boolean anyLow(MedicalState m, MedicalSettings s) {
        for (int i = 0; i < COUNT; i++) if (low(m, i, s)) return true;
        return false;
    }

    /** Выносливость и скорость: мало углеводов — быстро устаёт; много жира — тяжело. */
    static void gameplay(MedicalState m, GameplayEffects.Mods r, MedicalSettings s) {
        if (!s.nutritionEnabled || !m.nutritionKnown) return;
        if (low(m, CARBS, s)) {
            r.staminaCap *= s.carbsLowStaminaCap;
            r.speed = Math.max(s.minSpeedFactor, r.speed - s.carbsLowSpeedPenalty);
        }
        if (low(m, PROTEIN, s)) r.attackFactor = Math.max(0.2, r.attackFactor - s.proteinLowAttackPenalty);
        if (high(m, FAT, s)) {
            r.staminaCap *= s.fatHighStaminaCap;
            r.speed = Math.max(s.minSpeedFactor, r.speed - s.fatHighSpeedPenalty);
        }
    }
}
