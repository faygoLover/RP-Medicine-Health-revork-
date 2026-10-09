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
    /** Стойка капельницы (замечание 35): длина шланга, блоков; дальше — катетер вырван. */
    public double ivHoseLength = 4.0;
    /** Справочник: разделы открываются по уровню медицины (иначе всем всё видно). */
    public boolean bookSkillGating = false;
    /** Селезёнка от 50 %: внутреннее кровотечение в живот, мл/мин (от 80 % — вдвое). */
    public double spleenInternalBleed = 20.0;
    /** Без селезёнки иммунитет слабее (множитель). */
    public double aspleniaImmunityFactor = 0.7;
    /** Пробирка с кровью портится в тепле за столько часов (в термостате — нет). */
    public double sampleSpoilWarmHours = 2.0;
    /** С какого уровня медицины вообще можно сделать анализ крови в лаборатории. */
    public int labMinLevel = 3;
    /** Пакет с добавленным препаратом (норадреналин, пропофол) капает дольше — держит уровень, с. */
    public double ivAdditiveDripSeconds = 1200.0;
    /** Влитый раствор (физраствор, кровь) поит: процентов воды RP Culinary за литр. */
    public double ivWaterPercentPerLiter = 40.0;
    /** Всасывание препарата: укол в мышцу и таблетки — постоянная, с (почти всё всосалось за 3 таких срока). */
    public double drugAbsorbImSeconds = 35.0;
    public double drugAbsorbOralSeconds = 40.0;
    /** Рана от вырванного катетера (тяжесть пореза). */
    public double ivTearWoundSeverity = 6.0;

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
    public double concussionDecayPerSecond = 0.165;
    /** Ощущения (замечание 28): серость экрана с сознания ниже, звон с контузии, тяжёлое дыхание с ЧД выше / SpO2 ниже. */
    public double grayConsciousness = 80.0;
    public double ringingConcussion = 25.0;
    public double heavyBreathingRespRate = 20.0;
    public double heavyBreathingSpo2 = 93.0;
    public double concussionKnockoutLevel = 70.0;
    public double concussionKnockoutMinSeconds = 5.0;
    public double concussionKnockoutMaxSeconds = 15.0;
    /** Попадание в голову сильнее этого урона убивает сразу (0 — выключено). */
    public double instantDeathHeadDamage = 0.0;
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
    /** «Живучий»: раны и переломы заживают быстрее; «Хрупкий» — медленнее. */
    public double fastHealingFactor = 1.3;
    public double slowHealingFactor = 0.75;
    /** «Турист»: иммунитет сильнее; «Домашний» — слабее. */
    public double strongImmunityFactor = 1.3;
    public double weakImmunityFactor = 0.75;

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
    public double armUseSlowMain = 2.5;
    public double armUseSlowOff = 1.75;
    public double armAttackPenaltyMain = 0.3;
    public double armAttackPenaltyOff = 0.1;
    /** С какого уровня медицины на панели видны цифры состояния частей тела (ГМ видит всегда). */
    public int numbersMinLevel = 4;
    /** Урон по разрушенной части (целостность 0) переходит на остальное тело с множителем, как в Tarkov. */
    public boolean overflowEnabled = true;
    public double overflowArm = 0.49;
    public double overflowLeg = 0.7;
    public double overflowAbdomen = 1.05;
    /** Урон по разрушенной голове бьёт по мозгу: на единицу тяжести. */
    public double destroyedHeadBrainPerSeverity = 0.4;
    /** Проникающее ранение головы: мозг теряет столько процентов за единицу тяжести, контузия — столько. */
    public double headPenetratingBrainPerSeverity = 0.6;
    public double headPenetratingConcussionPerSeverity = 3.0;
    /** Урон по разрушенной груди бьёт по сердцу и лёгким: на единицу тяжести. */
    public double destroyedChestOrganPerSeverity = 0.5;
    /** Часть считается разрушенной при целостности ниже. */
    public double destroyedIntegrity = 5.0;
    /** Разрушенная голова или грудь: сознание не выше (нокдаун). */
    public double destroyedVitalConsciousness = 15.0;
    /** Ограничения переломов снимает только сильное обезболивание (не меньше) или укол адреналина; свой адреналин после ранения — нет. */
    public double fractureMaskAnalgesia = 45.0;
    /** Под обезболиванием сломанная рука всё равно работает хуже: доля штрафа. */
    public double maskedArmPenaltyShare = 0.5;
    /** Боль ноги или стопы (после обезболивания), с которой нога хромает и бег недоступен. */
    public double legPainLimpThreshold = 20.0;
    /** С этой болью ноги нельзя прыгать. */
    public double legPainNoJumpThreshold = 35.0;
    /** Минус скорость за каждую больную ногу или стопу. */
    public double legPainLimpPenalty = 0.15;
    /** Скорость ломания блоков: плохая рабочая рука, плохая вторая, обе сломаны. */
    public double armBreakSpeedMain = 0.5;
    public double armBreakSpeedOff = 0.8;
    public double armsDisabledBreakSpeed = 0.1;

    // ---------------- Госпиталь (второй этап) ----------------
    /** На больничной койке: заживление, восстановление крови и мозга быстрее во столько раз. */
    public double bedHealFactor = 1.5;
    public double bedBloodRegenFactor = 1.5;
    public double bedBrainRecoveryFactor = 2.0;
    /** Койка рядом с источником кислорода: потолок SpO2 возвращается к норме на эту долю потери. */
    public double oxygenTherapyFactor = 0.5;
    /** Монитор звучит тревогой при SpO2 ниже. */
    public double monitorAlarmSpo2 = 85.0;

    // ---------------- Инфекция, иммунитет, сепсис (второй этап, п. 5) ----------------
    public boolean infectionEnabled = true;
    /** Шанс заражения раны по типу. */
    public final EnumMap<WoundType, Double> infectionChance = new EnumMap<>(WoundType.class);
    /** Рана проверяется на заражение через столько минут в сети после ранения. */
    public double infectionCheckMinMinutes = 20.0;
    public double infectionCheckMaxMinutes = 60.0;
    /** Повязка снижает шанс заражения: ×(1 − это × качество повязки). */
    public double dressingInfectionReduction = 0.8;
    public double antisepticInfectionFactor = 0.3;
    public double dirtyWaterInfectionFactor = 1.5;
    public double nonSterileInfectionFactor = 1.5;
    public double bedInfectionFactor = 0.7;
    /** Пуля или осколок внутри дольше стольких часов в сети — повторная проверка раны с шансом ×2. */
    public double foreignBodyInfectionHours = 6.0;
    public double foreignBodyInfectionFactor = 2.0;
    /** Рост инфекции в ране и ответа иммунитета, % в час в сети. */
    public double infectionGrowthPerHour = 4.0;
    public double immuneGrowthPerHour = 3.0;
    /** Заражённая рана болит сильнее на. */
    public double infectionPain = 15.0;
    /** Иммунитет слабее при потере крови больше этой доли. */
    public double immunityBloodLossFraction = 0.3;
    public double immunityBloodLossFactor = 0.7;
    public double immunityHungerFactor = 0.8;
    public double immunitySepsisFactor = 0.8;
    public double immunityBedFactor = 1.1;
    public double immunitySleepFactor = 1.1;
    /** Сепсис растёт от каждой раны с инфекцией 100 %, % в час. */
    public double sepsisPerHourPerSource = 25.0;
    /** Испорченная кровь: сепсис сразу и рост, % в час, столько часов. */
    public double spoiledBloodSepsis = 30.0;
    public double spoiledBloodSepsisPerHour = 10.0;
    public double spoiledBloodSepsisHours = 2.0;
    /** Сепсис спадает под антибиотиком силы 10, % в час (пропорционально силе). */
    public double sepsisAntibioticDeclinePerHour = 50.0;
    /** Лёгкий сепсис (ниже 30 %) без источника и без антибиотика спадает сам, % в час. Тяжёлый — только под антибиотиком. */
    public double sepsisNaturalDeclinePerHour = 3.0;
    public double sepsisPressureDrop30 = 15.0;
    public double sepsisPressureDrop60 = 35.0;
    public double sepsisConsciousnessLimit = 80.0;
    public double sepsisSpo2Penalty = 5.0;
    /** Шанс фибрилляции при сепсисе от 60 %, в час. */
    public double sepsisFibrillationPerHour = 0.3;

    // ---------------- Кровь: группы и переливание (второй этап, п. 4) ----------------
    /** Распределение групп: O−, O+, A−, A+, B−, B+, AB−, AB+ (веса, не обязательно в сумме 100). */
    public double[] bloodTypeWeights = {6, 35, 5, 30, 3, 15, 1, 5};
    public double bloodBagVolume = 450.0;
    /** Пакет крови капает столько секунд. */
    public double transfusionSeconds = 240.0;
    /** Брать кровь у донора можно, пока он потерял не больше этой доли. */
    public double donationMaxLossFraction = 0.15;
    /** Реакция на несовместимую кровь: длительность после остановки капельницы, минуты. */
    public double transfusionReactionMinMinutes = 10.0;
    public double transfusionReactionMaxMinutes = 20.0;
    public double transfusionReactionFever = 2.0;
    public double transfusionReactionPain = 30.0;
    public double transfusionReactionPressureDrop = 30.0;
    public double transfusionReactionArrestChance = 0.25;
    /** Пакет вне холодильника портится за столько часов (время работы сервера), в холодильнике — за столько дней. */
    public double bloodSpoilWarmHours = 2.0;
    public double bloodSpoilFridgeDays = 7.0;

    // ---------------- Вывихи, пинцет, швы (второй этап, п. 7–8) ----------------
    public boolean dislocationsEnabled = true;
    /** Шанс вывиха от тупого удара по конечности на единицу тяжести (тяжесть 20 — 6 %). */
    public double dislocationChancePerSeverity = 0.003;
    /** Шанс вывиха плеча, когда лежачего поднимают на плечо. */
    public double carryDislocationChance = 0.02;
    public double dislocationPain = 30.0;
    /** Вывихнутая нога: минус скорость (меньше, чем перелом). */
    public double dislocationSpeedPenalty = 0.2;
    /** Вправление без обезболивания: боль и её длительность, секунды. */
    public double reductionPain = 70.0;
    public double reductionPainSeconds = 10.0;
    /** Неудачное вправление: шанс перелома. */
    public double reductionFractureChance = 0.1;
    /** Извлечение пули без обезболивания: боль и длительность (может дать болевой шок). */
    public double extractionPain = 90.0;
    public double extractionPainSeconds = 30.0;
    /** Ошибка пинцетом: порез, тяжесть. */
    public double extractionErrorSeverity = 10.0;
    /** Обезболивание не меньше этого снимает боль манипуляций. */
    public double procedureAnalgesia = 30.0;
    /** Зашитая рана заживает быстрее во столько раз; слабый шов кровит долей от обычного. */
    public double sutureHealFactor = 2.0;
    public double weakSutureBleedFactor = 0.3;

    // ---------------- Мини-игры (второй этап, п. 9) ----------------
    public boolean minigamesEnabled = true;
    /** Мини-игры и в бою (решение автора 06.10: по умолчанию мини-игры всегда, в бою тоже). */
    public boolean minigamesInCombat = true;
    /** Переливание крови лежачему (нокдаун, клиническая смерть) быстрее во столько раз. */
    public double dripDownedFactor = 2.5;
    /** Мешок Амбу возвращает такую долю потерянного «потолка» SpO2 (при повреждённых лёгких, шоке). */
    public double ambuCeilingFactor = 0.5;
    /** Разряд дефибриллятора возможен столько секунд после последней компрессии СЛР. */
    public double defibAfterCprSeconds = 30;
    /** Урон по медику или пациенту за столько секунд — «бой»: только прогресс-бар. */
    public double combatSeconds = 30.0;
    /** Качество мини-игры ниже — ошибка медика. */
    public double minigameFailQuality = 0.3;
    /** Можно отказаться от мини-игры: прогресс-бар дольше и ошибка чаще. */
    public boolean minigameRefuseAllowed = true;
    public double minigameRefuseTimeFactor = 2.0;
    public double minigameRefuseErrorFactor = 1.5;

    // ---------------- Голод, жажда, среда (второй этап, п. 12) ----------------
    /** Вода ниже этой доли — обезвоживание: объём крови для давления меньше (до доли ниже), иммунитет слабее. */
    public double dehydrationThreshold = 0.3;
    public double dehydrationVolumeLoss = 0.1;
    /** Сытость ниже этой доли — голод: заживление и иммунитет медленнее. */
    public double hungerThreshold = 0.3;
    public double hungerHealFactor = 0.7;
    /** Команда food add: ванильной сытости за час, единиц (из 20), и воды LSO за час (из 20). */
    public double foodLossPerHour = 4.0;
    public double lsoThirstLossPerHour = 4.0;
    /** Без LSO: сдвиг температуры тела в холодном и жарком биоме и в рыхлом снегу, °C. */
    public double coldBiomeTempShift = -1.5;
    public double hotBiomeTempShift = 1.0;
    public double powderSnowTempShift = -3.0;
    /** С LSO: на сколько °C тела одна единица температуры LSO за пределами нормы (16–24). */
    public double lsoTempScale = 0.25;
    /** Лихорадка поднимает температуру LSO: единиц на 1 °C жара. */
    public double feverToLso = 2.0;

    // ---------------- Органы (третий этап, п. 2) ----------------
    public boolean organsEnabled = true;
    /** Шанс задеть орган проникающей раной груди или живота: огнестрел, колотая, осколок. */
    public double gunshotOrganChance = 0.4;
    public double stabOrganChance = 0.25;
    public double shrapnelOrganChance = 0.3;
    /** Урон органу: от и до такой доли тяжести раны. */
    public double organDamagePerSeverityMin = 0.5;
    public double organDamagePerSeverityMax = 1.0;
    /** Тупой удар или взрыв по груди и животу сильнее этого урона — ушиб органа, доля урона. */
    public double bluntOrganMinDamage = 15.0;
    public double bluntOrganFactor = 0.3;
    /** Огнестрел в печень, почки, кишечник — рана сразу заражена (брюшная полость). */
    public boolean abdominalGunshotInfects = true;
    /** Ниже этого орган заживает сам, %; выше — только операцией. */
    public double organSelfHealLimit = 50.0;
    public double organHealPerHour = 2.0;
    /** Системные причины (в сети), % в час: сепсис ≥ 30 — все органы; SpO2 ниже порога — сердце; давление ниже порога — почки. */
    public double sepsisOrganPerHour = 2.0;
    public double hypoxiaOrganSpo2 = 80.0;
    public double hypoxiaHeartPerHour = 3.0;
    public double lowPressureKidneys = 70.0;
    public double lowPressureKidneysPerHour = 4.0;
    /** Сердце ≥ 50: шанс фибрилляции в час при нагрузке (пульс выше 130 или кровопотеря больше 30 %); ≥ 80 — давление ниже. */
    public double heartFibrillationPerHour = 3.0;
    public double heartFailurePressure = 15.0;
    /** Лёгкие ≥ 50 и ≥ 80: потолок SpO2 ниже на столько. */
    public double lungsSpo2Penalty50 = 10.0;
    public double lungsSpo2Penalty80 = 25.0;
    /** Печень ≥ 50 и ≥ 80: кровотечение сильнее во столько раз; 100 — внутреннее кровотечение в живот не меньше, мл/мин. */
    public double liverBleedFactor50 = 1.2;
    public double liverBleedFactor80 = 1.5;
    public double liverFailureInternalBleed = 20.0;
    /** Почки ≥ 80 и 100: мозг теряет % в час. */
    public double kidneyBrainPerHour80 = 1.0;
    public double kidneyBrainPerHour100 = 5.0;
    /** Нет печени или почек (изъяты): мозг теряет % в час — смерть за полчаса без пересадки. */
    public double missingOrganBrainPerHour = 200.0;
    /** Боль от органа: доля его повреждения добавляется к боли груди или живота. */
    public double organPainFactor = 0.25;

    // ---------------- Анестезия (третий этап, п. 3) ----------------
    /** Местная анестезия: доля оставшейся боли части. */
    public double localAnesthesiaPainFactor = 0.1;

    // ---------------- Ванилла (второй этап, п. 13) ----------------
    /** Отравление: тошнота держится ещё столько секунд после эффекта; рвота раз в столько секунд. */
    public double poisonNauseaSeconds = 10.0;
    public double poisonVomitIntervalSeconds = 20.0;
    /** Рвота: воды минус столько процентов (через RP Culinary; без него, с LSO — единиц воды LSO из 20). */
    public double vomitThirstLoss = 5.0;
    public double vomitLsoThirstLoss = 1.0;
    /** Мгновенный урон: острая боль за единицу урона и сколько секунд. */
    public double instantDamagePainPerDamage = 15.0;
    public double instantDamagePainSeconds = 60.0;
    /** Золотое яблоко: адреналин, секунд. */
    public double goldenAppleAdrenalineSeconds = 60.0;
    /** Тотем бессмертия: кровь не ниже этой доли нормы. */
    public double totemBloodFraction = 0.6;
    /** Высота стонов и вскриков женского персонажа (пол — в медкарте). */
    public double femaleVoicePitch = 1.3;
    /** Острая боль от процедуры в сознании на столе с фиксацией (воздуховод, интубация). */
    public double restrainedProcedurePain = 70;
    /** Хирургия (третий этап, п. 4): боль шага без обезболивания, разрез, кровотечение вскрытой части, мл/мин. */
    public double surgeryStepPain = 80;
    public double surgeryStepPainSeconds = 30;
    public double incisionSeverity = 15;
    public double openPartBleed = 300;
    public double clampedPartBleed = 20;
    /** Шанс заражения открытой части за минуту (до множителей). */
    public double openPartInfectionPerMinute = 0.04;
    /** Ошибка шага: порез соседних тканей, урон органу при раскрытой груди или животе. */
    public double surgeryErrorSeverity = 12;
    public double surgeryErrorOrganDamage = 15;
    /** Восстановление органа швами на раскрытой части. */
    public double organRepairAmount = 40;
    /** Остеосинтез: во сколько раз быстрее заживает перелом. */
    public double osteosynthesisHealFactor = 3;
    /** Место операции: множитель успеха и заражения (п. 4.2). */
    public double surgeryTableSuccess = 1.0;
    public double surgeryTableInfection = 0.3;
    public double surgeryBedSuccess = 0.9;
    public double surgeryBedInfection = 0.5;
    public double surgeryFieldSuccess = 0.8;
    public double surgeryFieldInfection = 1.0;
    public double surgeryFloorSuccess = 0.6;
    public double surgeryFloorInfection = 2.0;
    /** Нестерильный инструмент и нет маски или перчаток — заражение умножается. */
    public double surgeryNonSterileFactor = 1.5;
    public double surgeryNoMaskFactor = 1.3;
    public double surgeryNoGlovesFactor = 1.3;
    /** Износ (замечание 09.10, И38): перчатки грязнеют после стольких шагов операции (или сразу на другом пациенте),
     *  грязные переносят заразу сильнее, чем их отсутствие; маска отсыревает за столько минут на лице. */
    public int gloveUses = 40;
    public double dirtyGlovesFactor = 1.3;
    public int maskWearMinutes = 60;
    /** Стерилизатор: длительность цикла, секунды (замечание 10.10, Ф42). */
    public int sterilizeSeconds = 600;
    /** Некроз (третий этап, п. 5): с какого процента необратим, рост с причиной и без, выздоровление на антибиотике, %/ч. */
    public double necrosisIrreversible = 15;
    public double necrosisGrowthPerHour = 30;
    public double necrosisSelfGrowthPerHour = 5;
    public double necrosisRecoveryPerHour = 20;
    /** Тяжёлый сепсис: с какого уровня и шанс в час начать отмирать стопе или руке. */
    public double necrosisSepsisThreshold = 60;
    public double necrosisSepsisChancePerHour = 0.01;
    /** Необратимый некроз — источник сепсиса, %/ч за часть. */
    public double necrosisSepsisPerHour = 4;
    /** Ампутация (п. 6.1): культя под швами; травматическая — урон по сломанной конечности, шанс, боль. */
    public double stumpSeverity = 20;
    public boolean traumaticAmputation = true;
    public double traumaticAmputationDamage = 30;
    /** Взрыв делит урон по частям тела — для отрыва взрывом свой порог доли урона (замечание 89). */
    public double traumaticAmputationBlastDamage = 5;
    public double traumaticAmputationChance = 0.3;
    public double traumaticStumpSeverity = 60;
    public double traumaticAmputationPain = 90;
    /** Протезы (п. 6.2): потеря скорости. */
    public double prostheticFootSpeedPenalty = 0.15;
    public double pegLegSpeedPenalty = 0.30;
    /** Органы вне тела (п. 7): порча вне холодильника, ч; в холодильнике, сут; конечность пришить — в течение, ч; отторжение, %/ч. */
    public double organSpoilWarmHours = 1;
    public double organSpoilFridgeDays = 3;
    public double limbReattachHours = 6;
    public double rejectionPerHour = 10;
    /** Шов пришитой конечности. */
    public double reattachSeverity = 30;
    /** Диабет (п. 8): сахар, ммоль/л — снижение в час, от еды за единицу сытости, глюкоза, пороги. */
    public double sugarDeclinePerHour = 0.8;
    public double sugarPerNutrition = 0.5;
    public double glucoseTabletSugar = 3;
    public double sugarLow = 3.5;
    public double sugarFaint = 2.8;
    public double sugarBrainDamage = 2.5;
    public double hypoglycemiaBrainPerHour = 2;
    public double sugarHigh = 15;
    public double sugarComa = 25;
    public double hyperglycemiaThirstPerHour = 15;
    /** Курильщик и алкоголик: с какого повреждения лёгких и печени не восстанавливаются; кашель курильщика, раз в час. */
    public double smokerLungsFloor = 15;
    public double alcoholicLiverFloor = 20;
    public double smokerCoughsPerHour = 1;
    /** Вещества (п. 9): включены; множители прироста толерантности и шанса зависимости; спад толерантности, %/ч;
     *  ниже какой толерантности зависимость проходит; множитель задержки ломки; боль и замедление при ломке. */
    public boolean substancesEnabled = true;
    public double toleranceGainFactor = 1.0;
    public double dependenceFactor = 1.0;
    public double toleranceDecayPerHour = 2.0;
    public double dependenceLossTolerance = 10.0;
    public double withdrawalDelayFactor = 1.0;
    public double withdrawalPain = 20.0;
    public double withdrawalUseSlow = 1.3;
    /** Кофеиновая ломка (перк «Зависимость от кофеина» с RP Perks): вялость — медленнее ходьба, слабее удар, хуже восстановление. */
    public double caffeineWithdrawalSpeedPenalty = 0.10;
    /** Стрельба (Tacz Attribute Add, поверх навыка RP Perks): ведущая рука ранена или сломана. */
    public double gunMainArmSpread = 1.5;
    /** Телосложение: ожирение и лишний вес по доле жира, %; истощение — ИМТ ниже. */
    public double obeseFatPercent = 32;
    public double overweightFatPercent = 25;
    public double underweightBmi = 17.5;
    /** Ожирение: прибавка к давлению, потолок и восстановление выносливости. */
    public double obesePressure = 8;
    public double obeseStaminaCap = 0.85;
    public double obeseStaminaRegen = 0.9;
    /** Истощение: заживление и иммунитет. */
    public double underweightHeal = 0.85;
    public double underweightImmunity = 0.85;
    public double gunMainArmReload = 1.5;
    /** Вторая рука ранена: двуручное оружие держать хуже. */
    public double gunOffArmSpread = 1.2;
    public double gunOffArmReload = 1.3;
    /** Заметная боль (от 30) — разброс. */
    public double gunPainSpread = 1.2;
    /** Опьянение, тремор ломки — разброс. */
    public double gunTremorSpread = 1.3;
    public double caffeineWithdrawalAttack = 0.85;
    public double caffeineWithdrawalStaminaRegen = 0.7;
    /** Алкоголь: опьянение за дозу, спад в час; сильное, «вырубило», рвота; судороги при ломке (шанс в час, секунд). */
    public double alcoholIntoxicationPerDose = 18.0;
    public double alcoholDecayPerHour = 12.0;
    public double intoxicationHeavy = 70.0;
    public double intoxicationPassOut = 90.0;
    public double intoxicationVomit = 60.0;
    public double alcoholSeizureChancePerHour = 0.2;
    public double seizureSeconds = 20.0;
    /** Никотин: лёгкое обезболивание за дозу. */
    public double nicotineAnalgesia = 5.0;
    /** Никотин: прибавка к пульсу за дозу; дым: повреждение лёгких за дозу (проходит само). */
    public double nicotineHeartRate = 6.0;
    public double smokeLungDamage = 0.4;
    /** Перки: стартовая толерантность (и зависимость) алкоголика, курильщика, кофеинозависимого. */
    public double alcoholicTolerance = 60.0;
    public double smokerNicotineTolerance = 30.0;
    public double caffeineAddictTolerance = 30.0;
    /** Питание (запасы ведёт RP Culinary): действуют ли нехватка и избыток нутриентов на тело. */
    public boolean nutritionEnabled = true;
    /** Нехватка (ниже), избыток (выше), баланс (все в полосе). */
    public double nutrientLow = 20;
    public double nutrientHigh = 100;
    public double balancedMin = 35;
    public double balancedMax = 85;
    /** Нехватка: белок — заживление и удар; витамины — иммунитет и заживление; жир — мёрзнет; углеводы — выносливость и скорость. */
    public double proteinLowHealFactor = 0.7;
    public double proteinLowAttackPenalty = 0.15;
    public double vitaminsLowImmunityFactor = 0.7;
    public double vitaminsLowHealFactor = 0.85;
    public double fatLowColdShift = 1.5;
    public double carbsLowStaminaCap = 0.75;
    public double carbsLowSpeedPenalty = 0.05;
    /** Избыток жира — тяжело; баланс — бонус к заживлению и иммунитету. */
    public double fatHighStaminaCap = 0.85;
    public double fatHighSpeedPenalty = 0.05;
    public double balancedHealFactor = 1.15;
    public double balancedImmunityFactor = 1.1;
    /** Диабет: сахар за грамм углеводов. */
    public double sugarPerCarbGram = 0.06;
    /** Капельница у стойки и на койке идёт быстрее (в поле — обычная скорость, стоя на месте). */
    public double dripIvStandFactor = 1.5;
    public double dripBedFactor = 1.5;
    /** Дозы по весу: стандартная доза инъектора — на этот вес, кг; степень зависимости. */
    public boolean doseByWeight = true;
    public double doseReferenceWeight = 70.0;
    public double doseWeightExponent = 0.7;
    /** Действующая разовая доза выше этой доли нормы — растёт шанс передозировки. */
    public double overdoseDoseFactor = 1.4;
    /** С какого уровня медицины шприц с ампулой позволяет выбрать дозу. */
    public int dosingMinLevel = 4;
    /** Набор стабилизации: во сколько раз медленнее тает таймер нокдауна и сколько секунд. */
    public double stabilizationFactor = 0.5;
    public double stabilizationSeconds = 300.0;
    /** Тотем лечит каждую рану: доля тяжести = база + случайно до разброса + за уровень медицины. */
    public double totemHealBase = 0.2;
    public double totemHealRandom = 0.6;
    public double totemHealPerLevel = 0.04;
    /** Золотое яблоко: обезболивание (сила, секунды); зачарованное — вдвое сильнее и дольше. */
    public double goldenAppleAnalgesia = 15.0;
    public double goldenAppleAnalgesiaSeconds = 120.0;
    /** Взрыв: глухота от столько секунд (слабый) до столько (сильный); слабее этого урона — без глухоты. */
    public double explosionDeafMinSeconds = 30.0;
    public double explosionDeafMaxSeconds = 60.0;
    public double explosionDeafMinDamage = 3.0;
    public double explosionDeafMaxDamage = 15.0;
    /** Одышка искажает речь: ЧДД выше или SpO₂ ниже этих значений. */
    public double breathlessRespRate = 26.0;
    public double breathlessSpo2 = 90.0;

    // ---------------- Температура тела (второй этап, п. 5.4) ----------------
    public double normalBodyTemp = 36.6;
    /** Температура тела меняется не быстрее, °C в минуту. */
    public double bodyTempChangePerMinute = 0.1;
    /** Лихорадка от заражённой раны (при инфекции 100 %), °C. */
    public double localInfectionFever = 1.0;
    /** Лихорадка при сепсисе 10 % и 100 %, °C. */
    public double sepsisFeverMin = 1.4;
    public double sepsisFeverMax = 2.4;
    /** Пульс чаще на столько за каждый градус выше 37. */
    public double feverHeartRatePerDegree = 10.0;

    public MedicalSettings() {
        put(infectionChance, 0, 0.10, 0.10, 0.15, 0.15, 0.20, 0.30);
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
