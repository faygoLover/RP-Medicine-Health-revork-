package faygolover.rpmedicine.core;

/**
 * Телосложение (решения 08.10.2026): рост и вес были и раньше (объём крови, дозы), к ним добавлена доля жира.
 * ИМТ = вес / рост²; по ИМТ, жиру и Силе (RP Perks) видно, полный человек, худой или мускулистый.
 * Ожирение — выше давление, меньше выносливости, тяжелее реанимировать; истощение — хуже заживление и иммунитет.
 * Жир пока выставляет ГМ ({@code /rpmedicine set … fat}); дальше его будет менять мод питания (калории).
 */
public final class Body {
    private Body() {}

    public static double bmi(MedicalState m) {
        double h = Math.max(1.0, m.heightCm / 100.0);
        return m.weightKg / (h * h);
    }

    public static boolean obese(MedicalState m) {
        return m.bodyFat >= MedicalSettings.get().obeseFatPercent;
    }

    public static boolean overweight(MedicalState m) {
        return !obese(m) && m.bodyFat >= MedicalSettings.get().overweightFatPercent;
    }

    public static boolean underweight(MedicalState m) {
        return bmi(m) < MedicalSettings.get().underweightBmi || m.bodyFat < 5;
    }

    /** Тяжёлый, но не от жира — мышцы (Сила 7+). */
    public static boolean muscular(MedicalState m) {
        return m.strength >= 7 && m.bodyFat < 20 && bmi(m) >= 24;
    }

    /** Слово для осмотра или null (обычное телосложение). */
    public static String buildWord(MedicalState m) {
        if (obese(m)) return "build_obese";
        if (overweight(m)) return "build_overweight";
        if (muscular(m)) return "build_muscular";
        if (bmi(m) < 16) return "build_exhausted";
        if (underweight(m)) return "build_thin";
        return null;
    }
}
