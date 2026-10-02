package faygolover.rpmedicine.core;

import java.util.EnumMap;

/**
 * Все числа медицины. Значения по умолчанию — из ТЗ первого этапа; на сервере поля заполняет
 * {@code ServerConfig} при загрузке и перезагрузке конфига. Класс без зависимостей от Minecraft,
 * поэтому тесты создают его напрямую.
 */
public final class MedicalSettings {
    /** Текущие настройки сервера. Заменяется целиком при перезагрузке конфига. */
    private static volatile MedicalSettings current = new MedicalSettings();

    public static MedicalSettings get() { return current; }

    public static void set(MedicalSettings s) { current = s; }

    // ---------------- Общие ----------------
    /** Режим «без смерти»: вместо смерти — клиническая смерть. */
    public boolean noDeathMode = false;
    /** Шаг пересчёта физиологии, тики. */
    public int stepTicks = 10;

    // ---------------- Раны ----------------
    public double severityPerDamage = 5.0;
    public int maxWoundsPerPart = 6;
    /** Кровотечение раны, мл/мин на единицу тяжести. */
    public final EnumMap<WoundType, Double> bleedPerSeverity = new EnumMap<>(WoundType.class);
    /** Боль раны на единицу тяжести. */
    public final EnumMap<WoundType, Double> painPerSeverity = new EnumMap<>(WoundType.class);
    /** Заживление перевязанной раны, минуты в сети: при тяжести 0 и 100. */
    public final EnumMap<WoundType, Double> healMinutesMin = new EnumMap<>(WoundType.class);
    public final EnumMap<WoundType, Double> healMinutesMax = new EnumMap<>(WoundType.class);
    /** Во сколько раз дольше заживает рана без повязки. */
    public final EnumMap<WoundType, Double> undressedHealFactor = new EnumMap<>(WoundType.class);
    public double maxExternalBleedPerWound = 300.0;
    /** Пороги вида кровотечения, мл/мин: слабое до, среднее до, сильное до. */
    public double slightBleedMax = 30.0;
    public double moderateBleedMax = 150.0;
    public double heavyBleedMax = 300.0;
    /** Слабое кровотечение останавливается само за столько минут. */
    public double clotMinutes = 4.0;
    public double arterialBleedRate = 550.0;
    /** Внутреннее кровотечение: мл/мин на единицу тяжести вызвавшей раны и потолок. */
    public double internalBleedPerSeverity = 3.0;
    public double internalBleedMax = 200.0;
    /** Внутреннее кровотечение само спадает на эту долю в минуту. */
    public double internalBleedDecayPerMinute = 0.01;
    /** Ожог: тяжесть до этого порога — первая степень, до второго — вторая, выше — третья. */
    public double burnDegree2 = 25.0;
    public double burnDegree3 = 55.0;

    // ---------------- Повязки и жгуты ----------------
    /** Свежая повязка (минуты), которая может открыться от бега, прыжков, урона. */
    public double freshDressingMinutes = 5.0;
    public double reopenChancePerSprintSecond = 0.01;
    public double reopenChancePerJump = 0.04;
    public double reopenChanceOnDamage = 0.5;
    /** Жгут начинает вредить через столько минут. */
    public double tourniquetSafeMinutes = 15.0;
    /** Ишемия конечности под жгутом после безопасного времени, единиц в минуту. */
    public double tourniquetIschemiaPerMinute = 2.0;
    /** Шанс, что жгут Эсмарха порвётся при наложении. */
    public double esmarchBreakChance = 0.15;
    /** Ишемия проходит после снятия жгута, единиц в минуту. */
    public double ischemiaRecoveryPerMinute = 0.5;

    // ---------------- Кровь ----------------
    public double bloodPerKg = 70.0;
    public double defaultWeightKg = 70.0;
    public double defaultHeightCm = 175.0;
    public double bloodRegenPerHour = 500.0;
    /** Доля нормы крови, которую можно заменить физраствором. */
    public double salineMaxFraction = 1.0 / 3.0;
    /** Физраствор сам уходит из сосудов, доля в час. */
    public double salineLossPerHour = 0.25;
    public double salineVolume = 500.0;
    public double salineDripSeconds = 120.0;

    // ---------------- Множители ----------------
    public double bleedMultiplier = 1.0;
    public double painMultiplier = 1.0;
    public double healSpeedMultiplier = 1.0;

    // ---------------- Отключаемые системы ----------------
    public boolean fracturesEnabled = true;
    public boolean pneumothoraxEnabled = true;
    public boolean concussionEnabled = true;

    // ---------------- Переломы ----------------
    public double fracturePainClosed = 35.0;
    public double fracturePainOpen = 50.0;
    public double ribFracturePain = 25.0;
    public double splintPainFactor = 0.4;
    /** Открытый перелом кровит, мл/мин. */
    public double openFractureBleed = 60.0;
    /** Заживление перелома с шиной, минуты в сети. */
    public double fractureHealMinutesMin = 240.0;
    public double fractureHealMinutesMax = 300.0;
    /** Рёбра шину не принимают и заживают сами за это время. */
    public double ribHealMinutes = 270.0;
    /** Движение на сломанной ноге без шины под обезболиванием: ушиб, единиц тяжести за блок пути. */
    public double brokenLegWalkDamagePerBlock = 0.15;
    public double brokenLegOpenChancePerBlock = 0.002;

    // ---------------- Грудь ----------------
    public double pneumoSealMinSeconds = 120.0;
    public double pneumoSealMaxSeconds = 240.0;
    /** Напряжённый пневмоторакс доводит до остановки сердца за столько секунд. */
    public double tensionArrestSeconds = 180.0;
    public double openPneumoSpo2Penalty = 10.0;
    public double sealedPneumoSpo2Penalty = 4.0;
    public double tensionPneumoSpo2Penalty = 30.0;
    public double tensionPressureDrop = 60.0;
    public double ribSpo2Penalty = 3.0;

    // ---------------- Боль, адреналин, шок ----------------
    public double otherPainFactor = 0.25;
    public double painShockThreshold = 85.0;
    public double painShockMinSeconds = 20.0;
    public double painShockMaxSeconds = 30.0;
    /** Обморок от боли проходит, когда боль ниже порога на столько. */
    public double painShockRecoveryMargin = 15.0;
    public double adrenalineMinSeconds = 30.0;
    public double adrenalineMaxSeconds = 60.0;
    public double adrenalinePainSuppression = 35.0;
    public double painkillerStrength = 20.0;
    public double painkillerMinutes = 10.0;
    public double painkillerDelaySeconds = 60.0;
    public double morphineStrength = 60.0;
    public double morphineMinutes = 15.0;
    public double morphineDelaySeconds = 15.0;
    /** Шанс остановки дыхания от второй дозы морфина подряд. */
    public double morphineOverdoseArrestChance = 0.25;
    public double txaMinutes = 10.0;
    public double txaExternalFactor = 0.7;
    public double txaInternalFactor = 0.4;
    public double adrenalineInjectionSeconds = 120.0;
    public double adrenalineInjectionPressure = 25.0;
    /** Адреналин здоровому: шанс фибрилляции. */
    public double adrenalineHealthyFibrillationChance = 0.1;

    // ---------------- Давление, пульс, сердце ----------------
    public double normalPressure = 120.0;
    public double normalHeartRate = 72.0;
    public double normalRespRate = 14.0;
    /** Ниже этого давления несколько секунд подряд — фибрилляция. */
    public double fibrillationPressure = 40.0;
    public double fibrillationDelaySeconds = 10.0;
    /** Фибрилляция без помощи переходит в остановку за столько секунд. */
    public double fibrillationToArrestSeconds = 180.0;
    /** Потеря этой доли крови — остановка сердца. */
    public double arrestBloodLossFraction = 0.5;
    public double defibSuccessChance = 0.7;
    /** Шанс запуска сердца за каждые 5 секунд СЛР при введённом адреналине. */
    public double cprRestartChance = 0.15;
    /** СЛР даёт мозгу эту долю нормального кровотока. */
    public double cprPerfusion = 0.35;

    // ---------------- Дыхание и SpO2 ----------------
    public double spo2Normal = 98.0;
    public double spo2RisePerSecond = 1.5;
    public double spo2FallPerSecondApnea = 1.2;
    public double spo2FallPerSecondObstructed = 0.25;

    // ---------------- Сознание и мозг ----------------
    public double dazedConsciousness = 60.0;
    public double downedConsciousness = 30.0;
    public double knockdownMinSeconds = 180.0;
    public double knockdownMaxSeconds = 300.0;
    /** Новый урон по лежачему отнимает мозга на единицу урона. */
    public double downedDamageBrainPerDamage = 2.0;
    /** Мозг восстанавливается полностью за столько часов в сети. */
    public double brainRecoveryHours = 6.0;
    /** Гипоксия у бодрствующего: потеря мозга в секунду при SpO2 60 (линейно от 80). */
    public double hypoxiaBrainLossPerSecond = 0.08;
    public double wakeMinSeconds = 20.0;
    public double wakeMaxSeconds = 60.0;
    public double postClinicalHours = 3.0;
    public double concussionDecayPerSecond = 0.33;
    public double concussionKnockoutLevel = 70.0;
    public double concussionKnockoutMinSeconds = 5.0;
    public double concussionKnockoutMaxSeconds = 15.0;
    /** Попадание в голову сильнее этого урона убивает сразу (0 — выключено). */
    public double instantDeathHeadDamage = 24.0;
    /** Любое попадание сильнее этого урона убивает сразу (0 — выключено). */
    public double instantDeathDamage = 60.0;

    // ---------------- Навык ----------------
    public double[] skillSpeed = {0.6, 0.8, 1.0, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9};
    public double[] skillError = {0.25, 0.12, 0.05, 0.02, 0.01, 0.005, 0, 0, 0, 0, 0};
    /** Прибавка к шансу ошибки за каждый недостающий уровень до минимального уровня предмета. */
    public double underLevelErrorPerLevel = 0.3;
    public double maxErrorChance = 0.85;
    public double selfTreatTimeFactor = 1.5;
    /** Свои раны игрок видит не лучше этого уровня. */
    public int selfViewMaxLevel = 1;

    // ---------------- Перки ----------------
    public double toughShockBonus = 10.0;
    public double toughBleedFactor = 0.85;
    public double toughKnockdownFactor = 1.2;
    public double fragileFractureFactor = 1.5;
    public double fragilePainThresholdPenalty = 10.0;
    public double braveShockBonus = 10.0;
    public double cowardShockPenalty = 10.0;

    // ---------------- Последствия в игре ----------------
    public double limbIntegrityThreshold = 50.0;
    public double speedPenaltyPerLeg = 0.4;
    public double minSpeedFactor = 0.15;
    public double crawlSpeedFactor = 0.25;
    public double painLimpSpeedPenalty = 0.1;
    public double highPainSpeedPenalty = 0.15;
    public double dazedSpeedPenalty = 0.2;
    public double carrySpeedPenalty = 0.35;
    public double treatingSpeedPenalty = 0.5;
    public double armUseSlowMain = 1.5;
    public double armUseSlowOff = 1.25;
    public double armAttackPenaltyMain = 0.3;
    public double armAttackPenaltyOff = 0.1;

    public MedicalSettings() {
        put(bleedPerSeverity, 0, 2.0, 0.8, 6.0, 3.0, 0, 2.0);
        put(painPerSeverity, 1.0, 1.2, 1.4, 2.0, 1.8, 2.5, 1.2);
        put(healMinutesMin, 15, 30, 45, 120, 90, 60, 30);
        put(healMinutesMax, 30, 60, 90, 180, 150, 180, 60);
        put(undressedHealFactor, 1.0, 2.0, 2.0, 2.0, 2.0, 1.5, 2.0);
    }

    private static void put(EnumMap<WoundType, Double> map, double... values) {
        for (WoundType t : WoundType.VALUES) map.put(t, values[t.ordinal()]);
    }

    public double bleedPer(WoundType t) { return bleedPerSeverity.getOrDefault(t, 0.0); }

    public double painPer(WoundType t) { return painPerSeverity.getOrDefault(t, 1.0); }

    public double skillSpeed(int level) { return skillSpeed[clampLevel(level, skillSpeed.length)]; }

    public double skillError(int level) { return skillError[clampLevel(level, skillError.length)]; }

    private static int clampLevel(int level, int len) { return Math.max(0, Math.min(len - 1, level)); }

    public double normalBlood(double weightKg) { return bloodPerKg * weightKg; }
}
