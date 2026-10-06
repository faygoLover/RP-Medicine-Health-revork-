package faygolover.rpmedicine.config;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.HitLocator;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.WoundType;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Серверный конфиг (п. 11 ТЗ). Числа медицины лежат в {@link MedicalSettings}; здесь каждое поле
 * связывается с параметром конфига по имени. Остальные параметры слоя Minecraft — отдельными полями.
 */
public final class ServerConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final MedicalSettings DEFAULTS = new MedicalSettings();
    private static final List<Consumer<MedicalSettings>> APPLIERS = new ArrayList<>();

    // --- Параметры слоя Minecraft ---
    public static final ForgeConfigSpec.DoubleValue SEARCH_SECONDS;
    public static final ForgeConfigSpec.DoubleValue FINISH_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ADMIN_CALL_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEEP_AFTER_DEATH;
    public static final ForgeConfigSpec.BooleanValue STUBS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BLOCK_CARRY_ON;
    public static final ForgeConfigSpec.DoubleValue INTERACT_DISTANCE;
    /** Осмотр (панель) и обыск — только вплотную (замечание 7). */
    public static final ForgeConfigSpec.DoubleValue CLOSE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue CARRY_STAMINA_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue VANILLA_HEAL_BOOST_SECONDS;
    public static final ForgeConfigSpec.DoubleValue PROTECTED_ARMOR_EFFECT;
    // TaCZ и Zero Contact
    public static final ForgeConfigSpec.DoubleValue ZC_PENETRATION_RATIO;
    public static final ForgeConfigSpec.DoubleValue ZC_POWERFUL_ROUND_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ZC_HELMET_CONCUSSION;
    // Мобы
    public static final ForgeConfigSpec.DoubleValue MOB_BLEED_DAMAGE_PER_SEVERITY;
    public static final ForgeConfigSpec.DoubleValue MOB_BLEED_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MOB_STUN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue WARN_CONFLICTS;
    // Место попадания
    public static final ForgeConfigSpec.DoubleValue FEET_TOP;
    public static final ForgeConfigSpec.DoubleValue LEGS_TOP;
    public static final ForgeConfigSpec.DoubleValue ABDOMEN_TOP;
    public static final ForgeConfigSpec.DoubleValue CHEST_TOP;
    public static final ForgeConfigSpec.DoubleValue ARM_LATERAL;
    // Госпиталь
    public static final ForgeConfigSpec.IntValue MONITOR_MIN_LEVEL;
    public static final ForgeConfigSpec.DoubleValue MONITOR_VIEW_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue RANDOM_BLOOD_TYPE;

    static {
        B.comment("RP Medicine — серверный конфиг. Все значения по умолчанию — из ТЗ первого этапа.").push("general");
        bind("noDeathMode", "Режим «без смерти»: вместо смерти игрок попадает в клиническую смерть (п. 5.4 ТЗ). Настоящая смерть — «Сдаться», /kill, команда ГМа, пустота.");
        bindInt("stepTicks", "Шаг пересчёта физиологии, тики (20 тиков = 1 секунда).", 1, 100);
        SEARCH_SECONDS = B.comment("Обыск лежачего, секунды.").defineInRange("search_seconds", 10.0, 0.0, 600.0);
        FINISH_SECONDS = B.comment("Добивание: сколько секунд держать клавишу.").defineInRange("finish_seconds", 2.0, 0.0, 600.0);
        ADMIN_CALL_COOLDOWN_SECONDS = B.comment("«Позвать администратора» не чаще, секунды.").defineInRange("admin_call_cooldown_seconds", 180.0, 0.0, 86400.0);
        KEEP_AFTER_DEATH = B.comment("Что сохранять после смерти. Возможные значения: brain, blood, fractures, wounds, post_clinical. По умолчанию — ничего (рост и вес сохраняются всегда).")
                .defineListAllowEmpty(List.of("keep_after_death"), List::of, o -> o instanceof String);
        STUBS_ENABLED = B.comment("Оставлять заглушку (тело) лежачего игрока при выходе из игры (п. 5.5).").define("stubs_enabled", true);
        BLOCK_CARRY_ON = B.comment("Запрещать Carry On поднимать игроков и заглушки (у мода своя переноска).").define("block_carry_on", true);
        INTERACT_DISTANCE = B.comment("Дальность лечения, обыска, переноски и добивания, блоки.").defineInRange("interact_distance", 3.5, 1.0, 16.0);
        CLOSE_DISTANCE = B.comment("Дальность осмотра (панель по H) и обыска, блоки: только вплотную к пациенту.").defineInRange("close_distance", 2.0, 1.0, 16.0);
        CARRY_STAMINA_PER_SECOND = B.comment("Расход выносливости RP Stamina на переноску тела, в секунду.").defineInRange("carry_stamina_per_second", 4.0, 0.0, 10000.0);
        VANILLA_HEAL_BOOST_SECONDS = B.comment("Лечение от зелий и чужих модов не лечит, а ускоряет заживление: секунд ускорения на единицу лечения.").defineInRange("vanilla_heal_boost_seconds", 30.0, 0.0, 3600.0);
        PROTECTED_ARMOR_EFFECT = B.comment("Доля ванильного снижения урона броней, если попали в закрытую часть (1 — как в ванилле).").defineInRange("armor_effect", 1.0, 0.0, 2.0);
        WARN_CONFLICTS = B.comment("Предупреждать в логе о конфликтующих модах (Tactical Medicine, Player Revive).").define("warn_conflicts", true);
        B.pop();

        B.comment("Раны (п. 3.1).").push("wounds");
        bind("severityPerDamage", "Тяжесть раны на единицу урона (4 урона = рана тяжестью 20).");
        bindInt("maxWoundsPerPart", "Не больше записей ран на одну часть тела.", 1, 32);
        bindMap("bleedPerSeverity", "Кровотечение раны, мл/мин на единицу тяжести");
        bindMap("painPerSeverity", "Боль раны на единицу тяжести");
        bindMap("healMinutesMin", "Заживление перевязанной раны, минуты в сети, при малой тяжести");
        bindMap("healMinutesMax", "Заживление перевязанной раны, минуты в сети, при тяжести 100");
        bindMap("undressedHealFactor", "Во сколько раз дольше заживает рана без повязки");
        bind("maxExternalBleedPerWound", "Потолок наружного кровотечения одной раны, мл/мин.");
        bind("slightBleedMax", "Слабое кровотечение — до, мл/мин.");
        bind("moderateBleedMax", "Среднее кровотечение — до, мл/мин.");
        bind("heavyBleedMax", "Сильное кровотечение — до, мл/мин; выше — как артериальное.");
        bind("clotMinutes", "Слабое кровотечение останавливается само за столько минут.");
        bind("arterialBleedRate", "Артериальное кровотечение, мл/мин (без помощи до потери сознания около 4 минут).");
        bind("internalBleedPerSeverity", "Внутреннее кровотечение, мл/мин на единицу тяжести вызвавшей раны.");
        bind("internalBleedMax", "Потолок внутреннего кровотечения, мл/мин.");
        bind("internalBleedDecayPerMinute", "Внутреннее кровотечение само спадает на эту долю в минуту.");
        bind("burnDegree2", "Ожог тяжелее этого — вторая степень.");
        bind("burnDegree3", "Ожог тяжелее этого — третья степень.");
        bind("instantDeathHeadDamage", "Попадание в голову сильнее этого урона убивает сразу (0 — выключено; по умолчанию нокдаун наступает, когда голова разрушена).");
        bind("instantDeathDamage", "Любое попадание сильнее этого урона убивает сразу (0 — выключено).");
        bind("overflowEnabled", "Урон по разрушенной части (целостность 0) переходит на остальное тело, как в Tarkov.");
        bind("numbersMinLevel", "С какого уровня медицины на панели видны цифры состояния частей тела (ГМ видит всегда).");
        bind("overflowArm", "Множитель перехода урона с разрушенной руки.");
        bind("overflowLeg", "… с разрушенной ноги или стопы.");
        bind("overflowAbdomen", "… с разрушенного живота.");
        bind("destroyedHeadBrainPerSeverity", "Урон по разрушенной голове: мозг на единицу тяжести.");
        bind("destroyedChestOrganPerSeverity", "Урон по разрушенной груди: сердце и лёгкие на единицу тяжести.");
        bind("destroyedVitalConsciousness", "Разрушенная голова или грудь: сознание не выше (нокдаун).");
        bind("destroyedIntegrity", "Часть считается разрушенной при целостности ниже.");
        B.pop();

        B.comment("Повязки и жгуты (п. 6.2, 6.4).").push("dressings");
        bind("freshDressingMinutes", "Свежая повязка (минуты) может открыться от бега, прыжков и урона.");
        bind("reopenChancePerSprintSecond", "Шанс открытия свежей повязки за секунду бега.");
        bind("reopenChancePerJump", "Шанс открытия свежей повязки за прыжок.");
        bind("reopenChanceOnDamage", "Шанс открытия свежей повязки при новом уроне по части.");
        bind("tourniquetSafeMinutes", "Жгут начинает вредить конечности через столько минут.");
        bind("tourniquetIschemiaPerMinute", "Ишемия под жгутом после безопасного времени, единиц в минуту.");
        bind("ischemiaRecoveryPerMinute", "Ишемия проходит после снятия жгута, единиц в минуту.");
        bind("esmarchBreakChance", "Шанс, что жгут Эсмарха порвётся при наложении.");
        B.pop();

        B.comment("Кровь (п. 4.1).").push("blood");
        bind("bloodPerKg", "Объём крови, мл на кг веса.");
        bind("defaultWeightKg", "Вес по умолчанию, кг.");
        bind("defaultHeightCm", "Рост по умолчанию, см.");
        bind("bloodRegenPerHour", "Восстановление крови, мл в час в сети (только без кровотечения).");
        bind("salineMaxFraction", "Какую долю нормы крови можно заменить физраствором.");
        bind("salineLossPerHour", "Физраствор сам уходит из сосудов, доля в час.");
        bind("salineVolume", "Объём пакета физраствора, мл.");
        bind("salineDripSeconds", "Капельница идёт столько секунд.");
        bind("labMinLevel", "С какого уровня медицины можно сделать анализ крови в лаборатории.");
        bind("sampleSpoilWarmHours", "Пробирка с кровью портится в тепле за столько часов (в термостате и холодильнике — не портится).");
        bind("spleenInternalBleed", "Селезёнка от 50 %: внутреннее кровотечение в живот (от 80 % — вдвое).");
        bind("aspleniaImmunityFactor", "Без селезёнки иммунитет слабее (множитель).");
        bind("ivHoseLength", "Стойка капельницы: длина шланга, блоков. Пациент отошёл дальше — катетер вырван.");
        bind("ivAdditiveDripSeconds", "Пакет с добавленным препаратом (норадреналин, пропофол) капает столько секунд.");
        bind("ivTearWoundSeverity", "Рана от вырванного катетера (тяжесть пореза).");
        bind("drugAbsorbImSeconds", "Всасывание укола в мышцу (шприц-ручка), с: почти весь препарат в крови через 3 таких срока.");
        bind("drugAbsorbOralSeconds", "Всасывание таблеток, с: почти весь препарат в крови через 3 таких срока.");
        bind("arrestBloodLossFraction", "Потеря этой доли крови — остановка сердца.");
        B.pop();

        B.comment("Множители и отключаемые системы (п. 11).").push("multipliers");
        bind("bleedMultiplier", "Множитель всех кровотечений.");
        bind("painMultiplier", "Множитель боли.");
        bind("healSpeedMultiplier", "Множитель скорости заживления.");
        bind("fracturesEnabled", "Переломы включены.");
        bind("pneumothoraxEnabled", "Пневмоторакс включён.");
        bind("concussionEnabled", "Контузия включена.");
        B.pop();

        B.comment("Переломы и грудь (п. 3.2, 3.3).").push("fractures");
        bind("fracturePainClosed", "Боль закрытого перелома.");
        bind("fracturePainOpen", "Боль открытого перелома.");
        bind("ribFracturePain", "Боль перелома рёбер.");
        bind("splintPainFactor", "Шина уменьшает боль перелома до этой доли.");
        bind("openFractureBleed", "Открытый перелом кровит, мл/мин.");
        bind("fractureHealMinutesMin", "Перелом с шиной заживает, минуты в сети (закрытый ближе к этому).");
        bind("fractureHealMinutesMax", "Перелом с шиной заживает, минуты в сети (открытый).");
        bind("ribHealMinutes", "Перелом рёбер заживает сам, минуты в сети.");
        bind("brokenLegWalkDamagePerBlock", "Ходьба на сломанной ноге без шины: тяжесть ушиба за блок.");
        bind("brokenLegOpenChancePerBlock", "Ходьба на сломанной ноге без шины: шанс открытого перелома за блок.");
        bind("pneumoSealMinSeconds", "Открытый пневмоторакс без наклейки переходит в напряжённый не раньше, секунды.");
        bind("pneumoSealMaxSeconds", "… и не позже, секунды.");
        bind("tensionArrestSeconds", "Напряжённый пневмоторакс останавливает сердце за столько секунд.");
        bind("openPneumoSpo2Penalty", "Открытый пневмоторакс снижает потолок SpO2.");
        bind("sealedPneumoSpo2Penalty", "Заклеенный пневмоторакс снижает потолок SpO2.");
        bind("tensionPneumoSpo2Penalty", "Напряжённый пневмоторакс снижает потолок SpO2 (до).");
        bind("tensionPressureDrop", "Напряжённый пневмоторакс снижает давление (до).");
        bind("ribSpo2Penalty", "Перелом рёбер снижает потолок SpO2.");
        B.pop();

        B.comment("Боль, адреналин, шок, лекарства (п. 4.4, 6.2).").push("pain");
        bind("otherPainFactor", "Общая боль = сильнейшая плюс эта доля остальных.");
        bind("painShockThreshold", "Порог болевого шока.");
        bind("painShockMinSeconds", "Болевой шок копится до обморока не меньше, секунды.");
        bind("painShockMaxSeconds", "… и не больше, секунды.");
        bind("painShockRecoveryMargin", "Обморок от боли проходит, когда боль ниже порога на столько.");
        bind("adrenalineMinSeconds", "Выброс адреналина при ранении глушит боль не меньше, секунды.");
        bind("adrenalineMaxSeconds", "… и не больше, секунды.");
        bind("adrenalinePainSuppression", "Насколько адреналин глушит боль.");
        bind("painkillerStrength", "Сила таблеток обезболивающего.");
        bind("painkillerMinutes", "Таблетки действуют, минуты.");
        bind("painkillerDelaySeconds", "Таблетки начинают действовать через, секунды.");
        bind("morphineStrength", "Сила морфина.");
        bind("morphineMinutes", "Морфин действует, минуты.");
        bind("morphineDelaySeconds", "Морфин начинает действовать через, секунды.");
        bind("morphineOverdoseArrestChance", "Шанс остановки дыхания от второй дозы морфина подряд.");
        bind("txaMinutes", "Транексамовая кислота действует, минуты.");
        bind("txaExternalFactor", "Транексамовая кислота: множитель наружного кровотечения.");
        bind("txaInternalFactor", "Транексамовая кислота: множитель внутреннего кровотечения.");
        bind("adrenalineInjectionSeconds", "Укол адреналина действует, секунды.");
        bind("adrenalineInjectionPressure", "Укол адреналина поднимает давление на.");
        bind("adrenalineHealthyFibrillationChance", "Адреналин здоровому: шанс фибрилляции.");
        B.pop();

        B.comment("Сердце, давление, дыхание (п. 4.2, 4.3).").push("circulation");
        bind("normalPressure", "Нормальное (систолическое) давление.");
        bind("normalHeartRate", "Нормальный пульс.");
        bind("normalRespRate", "Нормальная частота дыхания.");
        bind("fibrillationPressure", "Давление ниже этого несколько секунд подряд — фибрилляция.");
        bind("fibrillationDelaySeconds", "Сколько секунд низкого давления до фибрилляции.");
        bind("fibrillationToArrestSeconds", "Фибрилляция без помощи переходит в остановку за, секунды.");
        bind("defibSuccessChance", "Шанс успешного разряда дефибриллятора.");
        bind("cprRestartChance", "Шанс запуска сердца за каждые 5 секунд СЛР при введённом адреналине.");
        bind("cprPerfusion", "СЛР даёт мозгу эту долю нормального кровотока.");
        bind("spo2Normal", "Нормальная сатурация.");
        bind("spo2RisePerSecond", "SpO2 растёт за секунду.");
        bind("spo2FallPerSecondApnea", "SpO2 падает за секунду без дыхания.");
        bind("spo2FallPerSecondObstructed", "SpO2 падает за секунду при западении языка.");
        B.pop();

        B.comment("Сознание, мозг, нокдаун (п. 4.5, 5.1).").push("brain");
        bind("dazedConsciousness", "Сознание ниже — оглушён.");
        bind("downedConsciousness", "Сознание ниже — лежачий.");
        bind("knockdownMinSeconds", "Нокдаун длится не меньше, секунды (при самых тяжёлых травмах).");
        bind("knockdownMaxSeconds", "Нокдаун длится не больше, секунды.");
        bind("downedDamageBrainPerDamage", "Урон по лежачему сокращает таймер: единиц мозга на единицу урона (мозг 100 = весь таймер).");
        bind("brainRecoveryHours", "Мозг восстанавливается полностью за столько часов в сети.");
        bind("hypoxiaBrainLossPerSecond", "Гипоксия без нокдауна: потеря мозга в секунду.");
        bind("wakeMinSeconds", "После устранения причины сознание возвращается не раньше, секунды.");
        bind("wakeMaxSeconds", "… и не позже, секунды.");
        bind("postClinicalHours", "Последствия клинической смерти, часов в сети.");
        bind("concussionDecayPerSecond", "Контузия проходит, единиц в секунду.");
        bind("concussionKnockoutLevel", "Контузия сильнее — короткая потеря сознания.");
        bind("concussionKnockoutMinSeconds", "Потеря сознания от контузии не меньше, секунды.");
        bind("concussionKnockoutMaxSeconds", "… и не больше, секунды.");
        B.pop();

        B.comment("Навык «Медицина» (п. 6.3).").push("skill");
        bindArray("skillSpeed", "Скорость применения по уровням 0–10.");
        bindArray("skillError", "Шанс ошибки по уровням 0–10.");
        bind("underLevelErrorPerLevel", "Прибавка к шансу ошибки за каждый недостающий уровень до минимального уровня предмета.");
        bind("maxErrorChance", "Потолок шанса ошибки.");
        bind("selfTreatTimeFactor", "На себе дольше во столько раз.");
        bindInt("selfViewMaxLevel", "Свои раны игрок видит не лучше этого уровня.", 0, 10);
        B.pop();

        B.comment("Перки RP Perks (п. 6.3).").push("perks");
        bind("toughShockBonus", "«Живучий», «Крепкий»: выше порог болевого шока на.");
        bind("toughBleedFactor", "«Живучий», «Крепкий»: множитель кровопотери.");
        bind("toughKnockdownFactor", "«Живучий», «Крепкий»: нокдаун длиннее во столько раз.");
        bind("fragileFractureFactor", "«Хрупкий», «Задохлик», «Слабак»: множитель шанса перелома.");
        bind("fragilePainThresholdPenalty", "«Хрупкий», «Задохлик», «Слабак»: ниже болевой порог на.");
        bind("braveShockBonus", "«Храбрый», «Волевой»: выше порог болевого шока на.");
        bind("cowardShockPenalty", "«Трусливый»: ниже порог болевого шока на.");
        B.pop();

        B.comment("Последствия в игре (п. 2.4).").push("gameplay");
        bind("limbIntegrityThreshold", "Конечность хуже работает при целостности ниже.");
        bind("speedPenaltyPerLeg", "Хромота: минус скорость за каждую плохую ногу или стопу.");
        bind("minSpeedFactor", "Скорость не ниже этой доли.");
        bind("crawlSpeedFactor", "Скорость ползком (обе ноги сломаны).");
        bind("painLimpSpeedPenalty", "Боль от 30: хромота, минус скорость.");
        bind("highPainSpeedPenalty", "Сильная боль (от 60): минус скорость.");
        bind("dazedSpeedPenalty", "Оглушение: минус скорость.");
        bind("carrySpeedPenalty", "Несущий тело: минус скорость.");
        bind("treatingSpeedPenalty", "Во время лечения: минус скорость.");
        bind("armUseSlowMain", "Плохая рабочая рука: использование предметов дольше во столько раз.");
        bind("armUseSlowOff", "Плохая вторая рука: использование предметов дольше во столько раз.");
        bind("armAttackPenaltyMain", "Плохая рабочая рука: удар слабее на долю.");
        bind("armAttackPenaltyOff", "Плохая вторая рука: удар слабее на долю.");
        bind("fractureMaskAnalgesia", "Ограничения переломов снимает обезболивание не слабее этого (морфин) или укол адреналина; свой адреналин после ранения — нет.");
        bind("maskedArmPenaltyShare", "Под обезболиванием сломанная рука всё равно хуже: доля штрафа (0–1).");
        bind("legPainLimpThreshold", "Боль ноги или стопы (после обезболивания), с которой нога хромает и бег недоступен.");
        bind("legPainNoJumpThreshold", "Боль ноги или стопы, с которой нельзя прыгать.");
        bind("legPainLimpPenalty", "Минус скорость за каждую больную ногу или стопу.");
        bind("armBreakSpeedMain", "Плохая рабочая рука: скорость ломания блоков (множитель).");
        bind("armBreakSpeedOff", "Плохая вторая рука: скорость ломания блоков.");
        bind("armsDisabledBreakSpeed", "Обе руки сломаны: скорость ломания блоков.");
        B.pop();

        B.comment("Место попадания по высоте, доля роста (п. 2.2).").push("hit_location");
        FEET_TOP = B.comment("Стопы — до.").defineInRange("feet_top", HitLocator.feetTop, 0.0, 1.0);
        LEGS_TOP = B.comment("Ноги — до.").defineInRange("legs_top", HitLocator.legsTop, 0.0, 1.0);
        ABDOMEN_TOP = B.comment("Живот — до.").defineInRange("abdomen_top", HitLocator.abdomenTop, 0.0, 1.0);
        CHEST_TOP = B.comment("Грудь — до; выше голова.").defineInRange("chest_top", HitLocator.chestTop, 0.0, 1.0);
        ARM_LATERAL = B.comment("Удар на высоте туловища дальше этой доли полуширины от оси — в руку.").defineInRange("arm_lateral", HitLocator.armLateral, 0.0, 1.0);
        B.pop();

        B.comment("TaCZ и Zero Contact (п. 3.4). Пока в Zero Contact нет события с исходом, исход определяется сравнением урона до и после брони.").push("guns");
        ZC_PENETRATION_RATIO = B.comment("Урон после брони не меньше этой доли от урона до неё — пробитие; иначе пулю остановила плита.").defineInRange("penetration_ratio", 0.45, 0.0, 1.0);
        ZC_POWERFUL_ROUND_DAMAGE = B.comment("Пуля, остановленная плитой, при уроне до брони от этого значения даёт внутреннее кровотечение.").defineInRange("powerful_round_damage", 8.0, 0.0, 1000.0);
        ZC_HELMET_CONCUSSION = B.comment("Пуля в шлем без пробития: контузия.").defineInRange("helmet_concussion", 25.0, 0.0, 100.0);
        B.pop();

        B.comment("Упрощённые травмы мобов (п. 12). Список мобов — в датапаке rpmedicine/mobs.").push("mobs");
        MOB_BLEED_DAMAGE_PER_SEVERITY = B.comment("Кровотечение моба: урон в секунду на 1 мл/мин кровотечения ран.").defineInRange("bleed_damage_per_ml", 0.003, 0.0, 10.0);
        MOB_BLEED_SECONDS = B.comment("Кровотечение моба длится, секунды.").defineInRange("bleed_seconds", 20.0, 0.0, 3600.0);
        MOB_STUN_SECONDS = B.comment("Болевой шок моба: оглушение, секунды.").defineInRange("stun_seconds", 2.0, 0.0, 60.0);
        B.pop();

        B.comment("Госпиталь (второй этап, п. 2). Какие блоки что делают — датапак rpmedicine/hospital_blocks.").push("hospital");
        bind("bedHealFactor", "На больничной койке заживление быстрее во столько раз.");
        bind("bedBloodRegenFactor", "На больничной койке кровь восстанавливается быстрее во столько раз.");
        bind("bedBrainRecoveryFactor", "На больничной койке мозг восстанавливается быстрее во столько раз.");
        bind("oxygenTherapyFactor", "Койка рядом с источником кислорода: потолок SpO2 возвращается к норме на эту долю потери (0–1).");
        bind("monitorAlarmSpo2", "Монитор звучит тревогой при SpO2 ниже (и при остановке сердца).");
        MONITOR_MIN_LEVEL = B.comment("Цифры монитора видит тот, у кого уровень «Медицины» не ниже (0 — все).").defineInRange("monitor_min_level", 0, 0, 10);
        MONITOR_VIEW_DISTANCE = B.comment("Цифры монитора видны с расстояния до, блоки.").defineInRange("monitor_view_distance", 6.0, 1.0, 32.0);
        B.pop();

        B.comment("Кровь: группы, забор, переливание (второй этап, п. 4).").push("blood_transfusion");
        bindArray("bloodTypeWeights", "Распределение групп крови при первом входе: O−, O+, A−, A+, B−, B+, AB−, AB+ (веса).");
        RANDOM_BLOOD_TYPE = B.comment("Группа крови задаётся случайно при первом входе (иначе — «не задана», пока ГМ не впишет командой).").define("random_blood_type", true);
        bind("bloodBagVolume", "Объём пакета крови, мл.");
        bind("transfusionSeconds", "Пакет крови капает столько секунд.");
        bind("donationMaxLossFraction", "Брать кровь можно, пока донор потерял не больше этой доли.");
        bind("transfusionReactionMinMinutes", "Реакция на несовместимую кровь длится после остановки капельницы не меньше, минуты.");
        bind("transfusionReactionMaxMinutes", "… и не больше, минуты.");
        bind("transfusionReactionFever", "Реакция: лихорадка, °C.");
        bind("transfusionReactionPain", "Реакция: боль в спине и груди.");
        bind("transfusionReactionPressureDrop", "Реакция: падение давления.");
        bind("transfusionReactionArrestChance", "Реакция: шанс фибрилляции в начале.");
        bind("bloodSpoilWarmHours", "Пакет вне холодильника портится за столько часов работы сервера.");
        bind("bloodSpoilFridgeDays", "Пакет в холодильнике портится за столько дней работы сервера.");
        B.pop();

        B.comment("Инфекция ран, иммунитет, сепсис (второй этап, п. 5). Время — в сети.").push("infection");
        bind("infectionEnabled", "Инфекция ран включена.");
        bindMap("infectionChance", "Шанс заражения раны");
        bind("infectionCheckMinMinutes", "Рана проверяется на заражение не раньше, минуты после ранения.");
        bind("infectionCheckMaxMinutes", "… и не позже, минуты.");
        bind("dressingInfectionReduction", "Повязка снижает шанс заражения: ×(1 − это × качество повязки).");
        bind("antisepticInfectionFactor", "Антисептик: множитель шанса заражения.");
        bind("dirtyWaterInfectionFactor", "Ранен в воде: множитель шанса заражения.");
        bind("nonSterileInfectionFactor", "Нестерильный инструмент: множитель шанса заражения.");
        bind("bedInfectionFactor", "Больничная койка: множитель шанса заражения.");
        bind("foreignBodyInfectionHours", "Пуля или осколок внутри дольше стольких часов — повторная проверка раны.");
        bind("foreignBodyInfectionFactor", "… с таким множителем шанса.");
        bind("infectionGrowthPerHour", "Рост инфекции в ране, % в час.");
        bind("immuneGrowthPerHour", "Ответ иммунитета на рану, % в час (кто первым дойдёт до 100).");
        bind("infectionPain", "Заражённая рана болит сильнее на.");
        bind("immunityBloodLossFraction", "Иммунитет слабее при потере крови больше этой доли…");
        bind("immunityBloodLossFactor", "… во столько раз.");
        bind("immunityHungerFactor", "Иммунитет при голоде и жажде: множитель.");
        bind("immunitySepsisFactor", "Иммунитет при сепсисе от 30 %: множитель.");
        bind("immunityBedFactor", "Иммунитет на больничной койке: множитель.");
        bind("immunitySleepFactor", "Иммунитет во сне (ванильная кровать): множитель.");
        bind("sepsisPerHourPerSource", "Сепсис растёт от каждой раны с инфекцией 100 %, % в час.");
        bind("spoiledBloodSepsis", "Испорченная кровь: сепсис сразу, %.");
        bind("spoiledBloodSepsisPerHour", "Испорченная кровь: рост сепсиса, % в час…");
        bind("spoiledBloodSepsisHours", "… в течение стольких часов.");
        bind("sepsisAntibioticDeclinePerHour", "Сепсис спадает под антибиотиком силы 10, % в час.");
        bind("sepsisNaturalDeclinePerHour", "Лёгкий сепсис (ниже 30 %) без источника и антибиотика спадает сам, % в час. Тяжёлый — только под антибиотиком.");
        bind("sepsisPressureDrop30", "Сепсис от 30 %: давление ниже на.");
        bind("sepsisPressureDrop60", "Сепсис от 60 %: давление ниже на.");
        bind("sepsisConsciousnessLimit", "Сепсис от 30 %: сознание не выше.");
        bind("sepsisSpo2Penalty", "Сепсис от 60 %: потолок SpO2 ниже на.");
        bind("sepsisFibrillationPerHour", "Сепсис от 60 %: шанс фибрилляции в час.");
        B.pop();

        B.comment("Вывихи, извлечение пуль, швы (второй этап, п. 7–8).").push("procedures");
        bind("dislocationsEnabled", "Вывихи включены.");
        bind("dislocationChancePerSeverity", "Шанс вывиха от тупого удара по конечности на единицу тяжести.");
        bind("carryDislocationChance", "Шанс вывиха плеча, когда лежачего поднимают на плечо.");
        bind("dislocationPain", "Боль вывиха.");
        bind("dislocationSpeedPenalty", "Вывихнутая нога или стопа: минус скорость.");
        bind("reductionPain", "Вправление без обезболивания: боль…");
        bind("reductionPainSeconds", "… столько секунд.");
        bind("reductionFractureChance", "Неудачное вправление: шанс перелома.");
        bind("extractionPain", "Извлечение пули без обезболивания: боль (может дать болевой шок)…");
        bind("extractionPainSeconds", "… столько секунд.");
        bind("extractionErrorSeverity", "Ошибка пинцетом: порез такой тяжести.");
        bind("procedureAnalgesia", "Обезболивание не меньше этого снимает боль манипуляций.");
        bind("sutureHealFactor", "Зашитая рана заживает быстрее во столько раз.");
        bind("weakSutureBleedFactor", "Слабый шов кровит такой долей от обычного.");
        B.pop();

        B.comment("Мини-игры лечения вне боя (второй этап, п. 9).").push("minigames");
        bind("minigamesEnabled", "Мини-игры включены (иначе всё прогресс-баром, как на первом этапе).");
        bind("combatSeconds", "«Бой»: медик или пациент получали урон за столько секунд — только прогресс-бар. Ещё бой — нокдаун с таймером и клиническая смерть.");
        bind("minigameFailQuality", "Качество мини-игры (0–1) ниже — ошибка медика.");
        bind("minigameRefuseAllowed", "Можно отказаться от мини-игры (и выбрать «без мини-игр» в настройках игрока): тогда прогресс-бар. Выключить — мини-игры обязательны и не пропускаются.");
        bind("headPenetratingBrainPerSeverity", "Проникающее ранение головы: мозг теряет столько процентов за единицу тяжести раны.");
        bind("headPenetratingConcussionPerSeverity", "Проникающее ранение головы: контузия за единицу тяжести (от 70 — нокаут).");
        bind("ambuCeilingFactor", "Мешок Амбу возвращает такую долю потерянного потолка SpO2 (повреждённые лёгкие, шок).");
        bind("dripDownedFactor", "Переливание крови лежачему (нокдаун, клиническая смерть) быстрее во столько раз.");
        bind("defibAfterCprSeconds", "Разряд дефибриллятора — в течение стольких секунд после компрессий СЛР.");
        bind("minigamesInCombat", "Мини-игры и в бою; выключить — в бою (урон за последние секунды) только прогресс-бар.");
        bind("minigameRefuseTimeFactor", "Отказ: прогресс-бар дольше во столько раз…");
        bind("minigameRefuseErrorFactor", "… и шанс ошибки выше во столько раз.");
        B.pop();

        B.comment("Голод, жажда, среда (второй этап, п. 12). С LSO жажду и температуру среды считает он.").push("survival");
        bind("ownThirstEnabled", "Своя жажда, если LSO не установлен (внутреннее значение, без полоски).");
        bind("thirstLossPerHour", "Своя жажда: убывает на столько процентов за час в сети.");
        bind("thirstSprintFactor", "На бегу жажда убывает быстрее во столько раз.");
        bind("dehydrationThreshold", "Вода ниже этой доли (0–1) — обезвоживание.");
        bind("dehydrationVolumeLoss", "При полном обезвоживании давление считается как при объёме крови меньше на эту долю.");
        bind("hungerThreshold", "Сытость ниже этой доли (0–1) — голод.");
        bind("hungerHealFactor", "Заживление при голоде: множитель.");
        bind("foodLossPerHour", "Команда food add: ванильной сытости за час, единиц из 20.");
        bind("lsoThirstLossPerHour", "Команда food add с LSO: воды LSO за час, единиц из 20.");
        bind("coldBiomeTempShift", "Без LSO: сдвиг температуры тела в холодном биоме, °C.");
        bind("hotBiomeTempShift", "Без LSO: сдвиг температуры тела в жарком биоме, °C.");
        bind("powderSnowTempShift", "Без LSO: сдвиг температуры тела в рыхлом снегу, °C.");
        bind("lsoTempScale", "С LSO: °C тела на одну единицу температуры LSO за пределами нормы (16–24).");
        bind("feverToLso", "Лихорадка поднимает температуру LSO: единиц на 1 °C жара.");
        B.pop();

        B.comment("Органы (третий этап, п. 2): повреждение 0–100 %.").push("organs");
        bind("organsEnabled", "Органы включены.");
        bind("gunshotOrganChance", "Шанс задеть орган огнестрелом в грудь или живот.");
        bind("stabOrganChance", "Шанс задеть орган колотой раной.");
        bind("shrapnelOrganChance", "Шанс задеть орган осколком.");
        bind("organDamagePerSeverityMin", "Урон органу: от такой доли тяжести раны…");
        bind("organDamagePerSeverityMax", "… до такой.");
        bind("bluntOrganMinDamage", "Тупой удар по груди или животу сильнее этого урона — ушиб органа.");
        bind("bluntOrganFactor", "Ушиб органа: доля урона.");
        bind("abdominalGunshotInfects", "Огнестрел в орган живота — рана сразу заражена.");
        bind("organSelfHealLimit", "Ниже этого (%) орган заживает сам; выше — только операцией.");
        bind("organHealPerHour", "Заживление органа, % в час в сети.");
        bind("sepsisOrganPerHour", "Сепсис от 30 %: все органы теряют столько % в час.");
        bind("hypoxiaOrganSpo2", "SpO2 ниже — сердце страдает от гипоксии…");
        bind("hypoxiaHeartPerHour", "… на столько % в час.");
        bind("lowPressureKidneys", "Давление ниже — почки страдают…");
        bind("lowPressureKidneysPerHour", "… на столько % в час.");
        bind("heartFibrillationPerHour", "Сердце от 50 %: шанс фибрилляции в час при нагрузке (пульс > 130 или кровопотеря > 30 %).");
        bind("heartFailurePressure", "Сердце от 80 %: давление ниже на столько.");
        bind("lungsSpo2Penalty50", "Лёгкие от 50 %: потолок SpO2 ниже на столько.");
        bind("lungsSpo2Penalty80", "Лёгкие от 80 %: потолок SpO2 ниже на столько.");
        bind("liverBleedFactor50", "Печень от 50 %: кровотечение сильнее во столько раз.");
        bind("liverBleedFactor80", "Печень от 80 %: кровотечение сильнее во столько раз.");
        bind("liverFailureInternalBleed", "Печень 100 %: внутреннее кровотечение в живот не меньше, мл/мин.");
        bind("kidneyBrainPerHour80", "Почки от 80 %: мозг теряет % в час.");
        bind("kidneyBrainPerHour100", "Почки 100 %: мозг теряет % в час.");
        bind("missingOrganBrainPerHour", "Нет печени или почек: мозг теряет % в час.");
        bind("organPainFactor", "Боль от органа: доля его повреждения.");
        B.pop();

        B.comment("Анестезия (третий этап, п. 3). Препараты — в датапаке drugs.").push("anesthesia");
        bind("localAnesthesiaPainFactor", "Местная анестезия: доля оставшейся боли части (0–1).");
        B.pop();

        B.comment("Ванильные эффекты (второй этап, п. 13).").push("vanilla");
        bind("poisonNauseaSeconds", "Отравление: тошнота держится ещё столько секунд после эффекта.");
        bind("poisonVomitIntervalSeconds", "Отравление: рвота раз в столько секунд.");
        bind("vomitThirstLoss", "Рвота: своей воды минус столько процентов.");
        bind("vomitLsoThirstLoss", "Рвота с LSO: его воды минус столько единиц из 20.");
        bind("instantDamagePainPerDamage", "Мгновенный урон (зелье, стрела, облако): острая боль за единицу урона.");
        bind("instantDamagePainSeconds", "Мгновенный урон: боль держится столько секунд.");
        bind("goldenAppleAdrenalineSeconds", "Золотое яблоко: адреналин, секунд.");
        bind("totemBloodFraction", "Тотем бессмертия: кровь не ниже этой доли нормы.");
        bind("stabilizationFactor", "Набор стабилизации: таймер нокдауна тает во столько раз медленнее.");
        bind("stabilizationSeconds", "Набор стабилизации: сколько секунд действует (один раз за нокдаун).");
        bind("doseByWeight", "Доза препарата действует по весу: стандартная доза — на эталонный вес.");
        bind("doseReferenceWeight", "Эталонный вес для стандартной дозы, кг.");
        bind("doseWeightExponent", "Насколько сильно вес меняет действие дозы (степень).");
        bind("overdoseDoseFactor", "Действующая разовая доза выше этой доли нормы — шанс передозировки.");
        bind("dosingMinLevel", "С какого уровня медицины шприц с ампулой позволяет выбрать дозу.");
        bind("dripIvStandFactor", "Во сколько раз быстрее капельница и переливание у стойки.");
        bind("dripBedFactor", "Во сколько раз быстрее капельница и переливание на койке (со стойкой — перемножается).");
        bind("femaleVoicePitch", "Высота стонов и вскриков женского персонажа (пол — в медкарте).");
        bind("restrainedProcedurePain", "Острая боль от процедуры в сознании на столе с фиксацией (воздуховод, интубация).");
        bind("substancesEnabled", "Вещества: толерантность, зависимость, ломка, опьянение.");
        bind("toleranceGainFactor", "Вещества: множитель прироста толерантности за дозу.");
        bind("dependenceFactor", "Вещества: множитель шанса зависимости.");
        bind("toleranceDecayPerHour", "Вещества: спад толерантности, % в час в сети.");
        bind("dependenceLossTolerance", "Вещества: ниже этой толерантности зависимость проходит.");
        bind("withdrawalDelayFactor", "Вещества: множитель времени до ломки.");
        bind("withdrawalPain", "Ломка: постоянная боль.");
        bind("withdrawalUseSlow", "Ломка: во сколько раз медленнее действия руками.");
        bind("alcoholIntoxicationPerDose", "Алкоголь: опьянение за порцию (0–100).");
        bind("alcoholDecayPerHour", "Алкоголь: трезвение в час.");
        bind("intoxicationHeavy", "Алкоголь: сильное опьянение — сознание мутнеет.");
        bind("intoxicationPassOut", "Алкоголь: вырубает.");
        bind("intoxicationVomit", "Алкоголь: с этого опьянения тошнит и рвёт.");
        bind("alcoholSeizureChancePerHour", "Ломка алкоголя: шанс судорог в час.");
        bind("seizureSeconds", "Ломка алкоголя: длительность судорог, с.");
        bind("nicotineAnalgesia", "Никотин: лёгкое обезболивание за дозу.");
        bind("alcoholicTolerance", "Перк «Закалённый алкоголик»: стартовая толерантность к алкоголю.");
        bind("smokerNicotineTolerance", "Перк «Курильщик»: стартовая толерантность к никотину.");
        bind("caffeineAddictTolerance", "Перк «Зависимость от кофеина»: стартовая толерантность к кофеину.");
        bind("nutritionEnabled", "Питание: белки, жиры, углеводы, витамины.");
        bind("proteinPerGram", "Питание: запас за грамм белка.");
        bind("fatPerGram", "Питание: запас за грамм жира.");
        bind("carbsPerGram", "Питание: запас за грамм углеводов.");
        bind("vitaminsPerUnit", "Питание: запас за единицу витаминов.");
        bind("proteinDecayPerHour", "Питание: трата белка в час в сети.");
        bind("fatDecayPerHour", "Питание: трата жира в час.");
        bind("carbsDecayPerHour", "Питание: трата углеводов в час.");
        bind("vitaminsDecayPerHour", "Питание: трата витаминов в час.");
        bind("sprintNutrientFactor", "Питание: во сколько раз быстрее трата при беге.");
        bind("nutrientMax", "Питание: предел запаса (переедание).");
        bind("nutrientLow", "Питание: ниже — нехватка.");
        bind("nutrientHigh", "Питание: выше — избыток.");
        bind("balancedMin", "Питание: баланс — все запасы не ниже.");
        bind("balancedMax", "Питание: баланс — все запасы не выше.");
        bind("proteinLowHealFactor", "Нехватка белка: множитель заживления.");
        bind("proteinLowAttackPenalty", "Нехватка белка: слабее удар.");
        bind("vitaminsLowImmunityFactor", "Нехватка витаминов: множитель иммунитета.");
        bind("vitaminsLowHealFactor", "Нехватка витаминов: множитель заживления.");
        bind("fatLowColdShift", "Нехватка жира: холоднее, °C.");
        bind("carbsLowStaminaCap", "Нехватка углеводов: потолок выносливости.");
        bind("carbsLowSpeedPenalty", "Нехватка углеводов: медленнее.");
        bind("fatHighStaminaCap", "Избыток жира: потолок выносливости.");
        bind("fatHighSpeedPenalty", "Избыток жира: медленнее.");
        bind("balancedHealFactor", "Сбалансированное питание: множитель заживления.");
        bind("balancedImmunityFactor", "Сбалансированное питание: множитель иммунитета.");
        bind("sugarPerCarbGram", "Диабет: сахар за грамм углеводов.");
        bind("monotonyEnabled", "«Приелось»: однообразная еда одного вида надоедает.");
        bind("monotonyThreshold", "«Приелось»: с какого числа недавних порций одного вида.");
        bind("monotonyDecayPerHour", "«Приелось»: сколько порций «забывается» в час в сети.");
        bind("monotonyVarietyRelief", "«Приелось»: другой вид еды освежает остальные на столько порций.");
        bind("monotonyUptakeStep", "«Приелось»: усвоение хуже на эту долю за порцию сверх порога.");
        bind("monotonyUptakeMin", "«Приелось»: усвоение не хуже этой доли.");
        bind("monotonyEatSlow", "«Приелось»: во сколько раз дольше есть.");
        bind("surgeryStepPain", "Хирургия: острая боль шага операции без обезболивания.");
        bind("surgeryStepPainSeconds", "Хирургия: сколько секунд держится боль шага.");
        bind("incisionSeverity", "Хирургия: тяжесть операционного разреза.");
        bind("openPartBleed", "Хирургия: кровотечение вскрытой части без зажима, мл/мин.");
        bind("clampedPartBleed", "Хирургия: кровотечение вскрытой части с зажимом, мл/мин.");
        bind("openPartInfectionPerMinute", "Хирургия: шанс заражения открытой части за минуту (до множителей места и стерильности).");
        bind("surgeryErrorSeverity", "Хирургия: ошибка шага — тяжесть пореза соседних тканей.");
        bind("surgeryErrorOrganDamage", "Хирургия: ошибка шага на раскрытой груди или животе — урон органу.");
        bind("organRepairAmount", "Хирургия: на сколько швы восстанавливают орган.");
        bind("osteosynthesisHealFactor", "Хирургия: во сколько раз быстрее заживает перелом после остеосинтеза.");
        bind("surgeryTableSuccess", "Место операции — операционный стол: множитель успеха.");
        bind("surgeryTableInfection", "Место операции — операционный стол: множитель заражения.");
        bind("surgeryBedSuccess", "Место операции — койка: множитель успеха.");
        bind("surgeryBedInfection", "Место операции — койка: множитель заражения.");
        bind("surgeryFieldSuccess", "Место операции — полевой хирургический набор у хирурга: множитель успеха.");
        bind("surgeryFieldInfection", "Место операции — полевой хирургический набор: множитель заражения.");
        bind("surgeryFloorSuccess", "Место операции — где угодно: множитель успеха.");
        bind("surgeryFloorInfection", "Место операции — где угодно: множитель заражения.");
        bind("surgeryNonSterileFactor", "Хирургия: нестерильный инструмент — множитель заражения.");
        bind("surgeryNoMaskFactor", "Хирургия: хирург без маски — множитель заражения.");
        bind("surgeryNoGlovesFactor", "Хирургия: хирург без перчаток — множитель заражения.");
        bind("totemHealBase", "Тотем: каждая рана лечится на долю тяжести — база…");
        bind("totemHealRandom", "… плюс случайно до этой доли…");
        bind("totemHealPerLevel", "… плюс за каждый уровень медицины держащего.");
        bind("goldenAppleAnalgesia", "Золотое яблоко: сила обезболивания (зачарованное — вдвое).");
        bind("goldenAppleAnalgesiaSeconds", "Золотое яблоко: обезболивание, секунды (зачарованное — вдвое).");
        bind("explosionDeafMinSeconds", "Взрыв: глухота от столько секунд (при слабом уроне)…");
        bind("explosionDeafMaxSeconds", "… до столько (при сильном).");
        bind("explosionDeafMinDamage", "Взрыв слабее этого урона не оглушает.");
        bind("explosionDeafMaxDamage", "Взрыв с таким уроном и сильнее — самая долгая глухота.");
        bind("breathlessRespRate", "Одышка искажает речь при ЧДД выше…");
        bind("breathlessSpo2", "… или SpO2 ниже.");
        B.pop();

        B.comment("Температура тела (второй этап, п. 5.4).").push("body_temperature");
        bind("normalBodyTemp", "Нормальная температура тела, °C.");
        bind("bodyTempChangePerMinute", "Температура меняется не быстрее, °C в минуту.");
        bind("localInfectionFever", "Лихорадка от заражённой раны (при инфекции 100 %), °C.");
        bind("sepsisFeverMin", "Лихорадка при сепсисе 10 %, °C над нормой.");
        bind("sepsisFeverMax", "Лихорадка при сепсисе 100 %, °C над нормой.");
        bind("feverHeartRatePerDegree", "Пульс чаще на столько за каждый градус выше 37.");
        B.pop();

        B.comment("Некроз, ампутации, протезы (третий этап, п. 6).").push("limbs");
        bind("necrosisIrreversible", "Некроз от этого процента — необратим (только ампутация).");
        bind("necrosisGrowthPerHour", "Некроз растёт на столько процентов в час, пока причина есть (жгут, ишемия, сепсис).");
        bind("necrosisSelfGrowthPerHour", "Необратимый некроз растёт сам на столько процентов в час.");
        bind("necrosisRecoveryPerHour", "Обратимый некроз под антибиотиком уходит на столько процентов в час.");
        bind("necrosisSepsisThreshold", "Сепсис от этого процента может запустить некроз рук и стоп.");
        bind("necrosisSepsisChancePerHour", "Шанс в час, что при таком сепсисе начнётся некроз конечности.");
        bind("necrosisSepsisPerHour", "Каждая необратимо омертвевшая часть добавляет столько процентов сепсиса в час.");
        bind("stumpSeverity", "Культя после ампутации: рана под швами такой тяжести.");
        bind("traumaticAmputation", "Травматическая ампутация: сильный удар может оторвать сломанную конечность.");
        bind("traumaticAmputationDamage", "Отрыв — при уроне по сломанной конечности больше этого (удар, пуля).");
        bind("traumaticAmputationBlastDamage", "Отрыв взрывом — при доле урона взрыва на сломанную конечность больше этого.");
        bind("traumaticAmputationChance", "Шанс отрыва при таком уроне.");
        bind("traumaticStumpSeverity", "Рана культи при отрыве.");
        bind("traumaticAmputationPain", "Острая боль при отрыве.");
        bind("prostheticFootSpeedPenalty", "Протез стопы: медленнее на эту долю.");
        bind("pegLegSpeedPenalty", "Деревянная нога: медленнее на эту долю (и без бега).");
        B.pop();

        B.comment("Органы и пересадка (третий этап, п. 7).").push("organs");
        bind("organSpoilWarmHours", "Орган вне холодильника портится за столько часов.");
        bind("organSpoilFridgeDays", "Орган в холодильнике портится за столько дней.");
        bind("limbReattachHours", "Отрезанную конечность можно пришить столько часов.");
        bind("rejectionPerHour", "Отторжение органа другой группы растёт на столько процентов в час (циклоспорин останавливает).");
        bind("reattachSeverity", "Пришитая конечность: рана такой тяжести.");
        B.pop();

        B.comment("Обмен веществ: диабет, курение, алкоголь (третий этап, п. 8).").push("metabolism");
        bind("sugarDeclinePerHour", "Сахар крови у диабетика падает на столько ммоль/л в час без еды.");
        bind("sugarPerNutrition", "Сахар растёт на столько от единицы углеводов еды.");
        bind("glucoseTabletSugar", "Таблетка глюкозы поднимает сахар на столько.");
        bind("sugarLow", "Гипогликемия: ниже этого — пот, слабость.");
        bind("sugarFaint", "Ниже этого — обморок.");
        bind("sugarBrainDamage", "Ниже этого — повреждение мозга.");
        bind("hypoglycemiaBrainPerHour", "Мозг теряет столько процентов в час при таком сахаре.");
        bind("sugarHigh", "Гипергликемия: выше этого — жажда, тошнота.");
        bind("sugarComa", "Выше этого — кома.");
        bind("hyperglycemiaThirstPerHour", "Жажда при высоком сахаре, единиц в час.");
        bind("smokerLungsFloor", "Курильщик: лёгкие не лучше этого (повреждение, %).");
        bind("alcoholicLiverFloor", "Алкоголик: печень не лучше этого (повреждение, %).");
        bind("smokerCoughsPerHour", "Курильщик кашляет столько раз в час.");
        B.pop();

        SPEC = B.build();
    }

    /** Загруженный файл конфига (для перечитывания командой reload). */
    public static net.minecraftforge.fml.config.ModConfig loaded;

    private ServerConfig() {}

    /** Перечитать файл с диска (команда {@code /rpmedicine reload}) и применить. */
    public static void reloadFromDisk() {
        if (loaded != null && loaded.getConfigData() instanceof com.electronwill.nightconfig.core.file.FileConfig fc) {
            fc.load();
            SPEC.afterReload();
        }
        apply();
    }

    /** Перечитывает конфиг в новый {@link MedicalSettings} и подменяет текущий. */
    public static void apply() {
        if (!SPEC.isLoaded()) return;
        MedicalSettings s = new MedicalSettings();
        for (Consumer<MedicalSettings> a : APPLIERS) a.accept(s);
        MedicalSettings.set(s);
        HitLocator.feetTop = FEET_TOP.get();
        HitLocator.legsTop = LEGS_TOP.get();
        HitLocator.abdomenTop = ABDOMEN_TOP.get();
        HitLocator.chestTop = CHEST_TOP.get();
        HitLocator.armLateral = ARM_LATERAL.get();
        RpMedicine.LOGGER.debug("RP Medicine: серверный конфиг применён");
    }

    // ------------------------------------------------------------------ связывание полей

    private static Field field(String name) {
        try {
            return MedicalSettings.class.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Нет поля настроек " + name, e);
        }
    }

    static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static void bind(String name, String comment) {
        Field f = field(name);
        try {
            if (f.getType() == double.class) {
                ForgeConfigSpec.DoubleValue v = B.comment(comment).defineInRange(snake(name), f.getDouble(DEFAULTS), 0.0, 1.0e9);
                APPLIERS.add(s -> set(f, s, v.get()));
            } else if (f.getType() == boolean.class) {
                ForgeConfigSpec.BooleanValue v = B.comment(comment).define(snake(name), f.getBoolean(DEFAULTS));
                APPLIERS.add(s -> set(f, s, v.get()));
            } else if (f.getType() == int.class) {
                ForgeConfigSpec.IntValue v = B.comment(comment).defineInRange(snake(name), f.getInt(DEFAULTS), 0, 1_000_000);
                APPLIERS.add(s -> set(f, s, v.get()));
            } else {
                throw new IllegalStateException("Неподдерживаемый тип " + name);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void bindInt(String name, String comment, int min, int max) {
        Field f = field(name);
        try {
            ForgeConfigSpec.IntValue v = B.comment(comment).defineInRange(snake(name), f.getInt(DEFAULTS), min, max);
            APPLIERS.add(s -> set(f, s, v.get()));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void bindMap(String name, String comment) {
        Field f = field(name);
        EnumMap<WoundType, Double> def;
        try {
            def = (EnumMap<WoundType, Double>) f.get(DEFAULTS);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        B.comment(comment + " — по типам ран.").push(snake(name));
        for (WoundType t : WoundType.VALUES) {
            ForgeConfigSpec.DoubleValue v = B.defineInRange(t.id, def.get(t), 0.0, 1.0e9);
            APPLIERS.add(s -> {
                try {
                    ((EnumMap<WoundType, Double>) f.get(s)).put(t, v.get());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        B.pop();
    }

    private static void bindArray(String name, String comment) {
        Field f = field(name);
        double[] def;
        try {
            def = (double[]) f.get(DEFAULTS);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        List<Double> defList = new ArrayList<>();
        for (double d : def) defList.add(d);
        ForgeConfigSpec.ConfigValue<List<? extends Double>> v = B.comment(comment + " Ровно " + def.length + " чисел.")
                .defineList(snake(name), defList, o -> o instanceof Number);
        APPLIERS.add(s -> {
            List<? extends Number> list = v.get();
            double[] arr = def.clone();
            for (int i = 0; i < arr.length && i < list.size(); i++) arr[i] = list.get(i).doubleValue();
            try {
                f.set(s, arr);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void set(Field f, MedicalSettings s, Object value) {
        try {
            f.set(s, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean keepAfterDeath(String what) {
        for (String s : KEEP_AFTER_DEATH.get()) if (s.toLowerCase(Locale.ROOT).equals(what)) return true;
        return false;
    }
}
