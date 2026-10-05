package faygolover.rpmedicine.core;

/**
 * Питание (решения, п. 1.12: «как в Health & Disease»; состав блюд — как в Nutritional Balance). У еды —
 * КБЖУ и витамины на порцию; у человека — запасы белков, жиров, углеводов и витаминов 0–100 (выше 100 —
 * переедание), которые тратятся в сети. Нехватка и избыток бьют по заживлению, иммунитету, выносливости,
 * холоду; сбалансированное питание помогает.
 */
public final class Nutrition {
    private Nutrition() {}

    public static final int PROTEIN = 0, FAT = 1, CARBS = 2, VITAMINS = 3, COUNT = 4;
    public static final String[] IDS = {"protein", "fat", "carbs", "vitamins"};
    /**
     * Виды еды для «приелось»: однообразная еда одного вида надоедает (по сравнению с Hanger System Change —
     * по виду, а не по предмету, мягко и с отыгрышем). Вид блюда — вид ингредиента с наибольшими калориями.
     */
    public static final String[] CATEGORIES = {"meat", "fish", "grain", "vegetables", "fruit", "sweet", "dairy"};

    public static int category(String id) {
        for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(id)) return i;
        return -1;
    }

    /** Приелось: этот вид ели слишком часто, и человек не голоден (голодному не до вкуса). */
    public static boolean fedUp(MedicalState m, int cat, MedicalSettings s) {
        return s.nutritionEnabled && s.monotonyEnabled && cat >= 0 && cat < CATEGORIES.length
                && m.monotony[cat] >= s.monotonyThreshold && !anyLow(m, s);
    }

    /** Битовая маска видов, которые сейчас приелись. */
    public static int fedUpMask(MedicalState m, MedicalSettings s) {
        int mask = 0;
        for (int i = 0; i < CATEGORIES.length; i++) if (fedUp(m, i, s)) mask |= 1 << i;
        return mask;
    }

    /** Плохой аппетит — что-то приелось сильно. */
    public static boolean poorAppetite(MedicalState m, MedicalSettings s) {
        for (int i = 0; i < CATEGORIES.length; i++) if (fedUp(m, i, s) && m.monotony[i] >= s.monotonyThreshold + 2) return true;
        return false;
    }

    /**
     * Съесть порцию вида {@code cat} (−1 — вид не считается). Возвращает true, если еда приелась: усваивается
     * хуже (не ниже {@code monotonyUptakeMin}). Другой вид еды чуть «освежает» остальные.
     */
    public static boolean eat(MedicalState m, Food f, int cat, MedicalSettings s) {
        boolean fed = fedUp(m, cat, s);
        double k = 1;
        if (fed) k = Math.max(s.monotonyUptakeMin, 1 - s.monotonyUptakeStep * (m.monotony[cat] - s.monotonyThreshold + 1));
        eat(m, f.scale(k), s);
        if (s.monotonyEnabled && cat >= 0 && cat < CATEGORIES.length) {
            for (int i = 0; i < CATEGORIES.length; i++)
                m.monotony[i] = i == cat ? m.monotony[i] + 1 : Math.max(0, m.monotony[i] - s.monotonyVarietyRelief);
        }
        return fed;
    }

    /** Состав порции: ккал, белки, жиры, углеводы (г), витамины (условные единицы). */
    public record Food(double kcal, double protein, double fat, double carbs, double vitamins) {
        public static final Food ZERO = new Food(0, 0, 0, 0, 0);

        public Food plus(Food o) {
            return new Food(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs, vitamins + o.vitamins);
        }

        public Food scale(double k) {
            return new Food(kcal * k, protein * k, fat * k, carbs * k, vitamins * k);
        }

        public boolean isEmpty() {
            return kcal <= 0 && protein <= 0 && fat <= 0 && carbs <= 0 && vitamins <= 0;
        }

        /** Калории по БЖУ, если не заданы: 4/9/4 ккал на грамм. */
        public Food withKcal() {
            return kcal > 0 ? this : new Food(protein * 4 + fat * 9 + carbs * 4, protein, fat, carbs, vitamins);
        }
    }

    public static void eat(MedicalState m, Food f, MedicalSettings s) {
        if (!s.nutritionEnabled || f == null) return;
        m.nutrients[PROTEIN] = Math.min(s.nutrientMax, m.nutrients[PROTEIN] + f.protein() * s.proteinPerGram);
        m.nutrients[FAT] = Math.min(s.nutrientMax, m.nutrients[FAT] + f.fat() * s.fatPerGram);
        m.nutrients[CARBS] = Math.min(s.nutrientMax, m.nutrients[CARBS] + f.carbs() * s.carbsPerGram);
        m.nutrients[VITAMINS] = Math.min(s.nutrientMax, m.nutrients[VITAMINS] + f.vitamins() * s.vitaminsPerUnit);
        m.kcalEaten += f.kcal();
    }

    /** Трата запасов в сети (быстрее при беге), спад счётчика калорий за последние часы. */
    public static void tick(MedicalState m, StepInput in, MedicalSettings s) {
        if (!s.nutritionEnabled || !in.online) return;
        double hours = in.dt / 3600.0;
        double activity = 1 + Math.min(1, in.sprintSeconds / Math.max(0.1, in.dt)) * (s.sprintNutrientFactor - 1);
        double[] decay = {s.proteinDecayPerHour, s.fatDecayPerHour, s.carbsDecayPerHour, s.vitaminsDecayPerHour};
        for (int i = 0; i < COUNT; i++) m.nutrients[i] = Math.max(0, m.nutrients[i] - decay[i] * hours * activity);
        m.kcalEaten = Math.max(0, m.kcalEaten * (1 - Math.min(1, hours / 6)));
        for (int i = 0; i < CATEGORIES.length; i++) m.monotony[i] = Math.max(0, m.monotony[i] - s.monotonyDecayPerHour * hours);
        // Условия шага: нехватка и баланс.
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
        return s.nutritionEnabled && m.nutrients[i] < s.nutrientLow;
    }

    public static boolean high(MedicalState m, int i, MedicalSettings s) {
        return s.nutritionEnabled && m.nutrients[i] > s.nutrientHigh;
    }

    public static boolean balanced(MedicalState m, MedicalSettings s) {
        if (!s.nutritionEnabled) return false;
        for (double v : m.nutrients) if (v < s.balancedMin || v > s.balancedMax) return false;
        return true;
    }

    public static boolean anyLow(MedicalState m, MedicalSettings s) {
        for (int i = 0; i < COUNT; i++) if (low(m, i, s)) return true;
        return false;
    }

    /** Выносливость и скорость: мало углеводов — быстро устаёт; много жира — тяжело. */
    static void gameplay(MedicalState m, GameplayEffects.Mods r, MedicalSettings s) {
        if (!s.nutritionEnabled) return;
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
