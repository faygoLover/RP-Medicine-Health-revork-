package faygolover.rpmedicine.core;

/**
 * Рана на части тела. Тяжесть уменьшается по мере заживления; кровотечение и боль считаются от
 * текущей тяжести. Время в секундах — время игрока в сети.
 */
public final class Wound {
    public WoundType type;
    /** Текущая тяжесть 0–100. */
    public double severity;
    /** Тяжесть на момент последнего ранения: от неё считается скорость заживления. */
    public double peakSeverity;
    public Dressing dressing = Dressing.NONE;
    /** Качество повязки 0–1: ошибка медика даёт повязку, которая держит хуже. */
    public double dressingQuality = 1.0;
    /** Сколько секунд прошло с наложения повязки (для «свежей» повязки). */
    public double dressingAge;
    /** Свёртывание слабого кровотечения 0–1. */
    public double clot;
    /** Бинт ускоряет заживление. */
    public boolean bandageBoost;

    // ---------------- Инфекция (второй этап, п. 5) ----------------
    /** Стадия: новая (проверка не назначена), ждёт проверки, чистая, заражена. Порядок — в сохранении. */
    public enum Infection {
        NEW, PENDING, CLEAN, INFECTED;

        public static Infection byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NEW;
        }
    }

    public Infection infectionStage = Infection.NEW;
    /** Секунд в сети до проверки на заражение. */
    public double infectionTimer;
    /** Инфекция в ране 0–100. */
    public double infection;
    /** Ответ иммунитета на эту рану 0–100: дошёл до 100 раньше инфекции — рана очистилась. */
    public double immuneProgress;
    /** Множитель шанса заражения: антисептик, грязная вода, нестерильный инструмент. */
    public double infectionRisk = 1.0;

    public Wound(WoundType type, double severity) {
        this.type = type;
        this.severity = clampSeverity(severity);
        this.peakSeverity = this.severity;
    }

    public static double clampSeverity(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    /** Кровотечение без учёта повязки, мл/мин. */
    public double rawBleed(MedicalSettings s) {
        return Math.min(s.maxExternalBleedPerWound, s.bleedPer(type) * severity);
    }

    /** Кровотечение с учётом повязки и свёртывания, мл/мин. */
    public double bleed(MedicalSettings s) {
        double raw = rawBleed(s);
        if (raw <= 0) return 0;
        double f = dressing.bleedFactor(raw, s);
        // Плохо наложенная повязка пропускает часть крови.
        f = f + (1.0 - f) * (1.0 - dressingQuality);
        double bleed = raw * f;
        if (raw <= s.slightBleedMax) bleed *= (1.0 - clot);
        return bleed;
    }

    public double pain(MedicalSettings s) {
        double p = s.painPer(type) * severity;
        if (isInfected()) p += s.infectionPain;
        return Math.min(100.0, p);
    }

    public boolean isInfected() {
        return infectionStage == Infection.INFECTED;
    }

    public boolean isDressed() {
        return dressing != Dressing.NONE;
    }

    /** Свежая повязка может открыться от бега, прыжков, нового урона. Гемостатик не открывается. */
    public boolean isFreshDressing(MedicalSettings s) {
        return dressing != Dressing.NONE && dressing != Dressing.HEMOSTATIC && dressingAge < s.freshDressingMinutes * 60.0;
    }

    public void removeDressing() {
        dressing = Dressing.NONE;
        dressingQuality = 1.0;
        dressingAge = 0;
        bandageBoost = false;
    }

    /** Степень ожога 1–3. */
    public int burnDegree(MedicalSettings s) {
        if (severity >= s.burnDegree3) return 3;
        if (severity >= s.burnDegree2) return 2;
        return 1;
    }

    public Wound copy() {
        Wound w = new Wound(type, severity);
        w.peakSeverity = peakSeverity;
        w.dressing = dressing;
        w.dressingQuality = dressingQuality;
        w.dressingAge = dressingAge;
        w.clot = clot;
        w.bandageBoost = bandageBoost;
        w.infectionStage = infectionStage;
        w.infectionTimer = infectionTimer;
        w.infection = infection;
        w.immuneProgress = immuneProgress;
        w.infectionRisk = infectionRisk;
        return w;
    }
}
