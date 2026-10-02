package faygolover.rpmedicine.core;

/** Навык «Медицина» 0–10 (п. 6.3 ТЗ): скорость и шанс ошибки. */
public final class Skill {
    private Skill() {}

    public static final int MAX_LEVEL = 10;

    public static int clamp(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** Множитель скорости применения (больше — быстрее). */
    public static double speed(int level, MedicalSettings s) {
        return s.skillSpeed(clamp(level));
    }

    /** Шанс ошибки с учётом минимального уровня предмета: ниже минимума — высокий. */
    public static double errorChance(int level, int minLevel, MedicalSettings s) {
        int lvl = clamp(level);
        double e = s.skillError(lvl);
        if (lvl < minLevel) e += (minLevel - lvl) * s.underLevelErrorPerLevel;
        return Math.min(s.maxErrorChance, Math.max(0, e));
    }

    /** Время применения в секундах. */
    public static double applySeconds(double baseSeconds, int level, boolean self, double armFactor, MedicalSettings s) {
        double t = baseSeconds / speed(level, s);
        if (self) t *= s.selfTreatTimeFactor;
        return t * armFactor;
    }
}
