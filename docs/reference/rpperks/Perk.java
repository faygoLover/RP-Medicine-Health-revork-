package faygolover.rpperks.perk;

import faygolover.rpperks.Config;

import java.util.Optional;

/**
 * Реестр всех перков.
 * Цена здесь — только значение по умолчанию: реальная цена берётся из конфига
 * (секция перка, параметр cost). Несовместимости тоже задаются в конфиге ([conflicts]).
 */
public enum Perk {
    // === Основные (положительные) ===
    SURVIVOR("survivor", Category.MAIN, 7, "Живучий"),
    ATHLETE("athlete", Category.MAIN, 7, "Атлет"),
    TOUGH("tough", Category.MAIN, 6, "Крепкий"),
    STRONGMAN("strongman", Category.MAIN, 5, "Силач"),
    TOURIST("tourist", Category.MAIN, 5, "Турист"),
    ORGANIZED("organized", Category.MAIN, 5, "Организованный"),
    LUCKY("lucky", Category.MAIN, 5, "Удачливый"),
    ENDURING("enduring", Category.MAIN, 4, "Выносливый"),
    BRAVE("brave", Category.MAIN, 4, "Храбрый"),
    STRONG_WILLED("strong_willed", Category.MAIN, 4, "Волевой"),
    NERD_RAGE("nerd_rage", Category.MAIN, 4, "Бешенство ботаника"),
    AGILE("agile", Category.MAIN, 3, "Ловкий"),
    SOLAR_POWERED("solar_powered", Category.MAIN, 3, "Солнечная батарейка"),
    LIGHT_TRAVEL("light_travel", Category.MAIN, 3, "Путешествие налегке"),
    ALCOHOLIC("alcoholic", Category.MAIN, 2, "Закалённый алкоголик"),
    SMOKER("smoker", Category.MAIN, 1, "Курильщик"),

    // === Умения ===
    SOLDIER("soldier", Category.SKILL, 9, "Подготовленный солдат"),
    MEDIC("medic", Category.SKILL, 7, "Анастасия"),
    MELEE_FIGHTER("melee_fighter", Category.SKILL, 7, "Боец ближнего боя"),
    BUILDER("builder", Category.SKILL, 6, "Рогалёв"),
    GUNNER("gunner", Category.SKILL, 5, "Курсы владения оружием"),
    EXPLOSIVES("explosives", Category.SKILL, 5, "Взрывчатка"),
    TECHNICIAN("technician", Category.SKILL, 5, "Дентон"),
    HACKER("hacker", Category.SKILL, 5, "Взлом"),
    SAILOR("sailor", Category.SKILL, 4, "Морской"),
    TAILOR("tailor", Category.SKILL, 4, "Кройка"),
    FARMER("farmer", Category.SKILL, 4, "Фермер"),
    COOK("cook", Category.SKILL, 4, "Кулинар"),
    DRIVER("driver", Category.SKILL, 4, "Водятел"),
    PILOT("pilot", Category.SKILL, 4, "Пинпиныч"),
    FIRST_AID("first_aid", Category.SKILL, 3, "Курсы первой медицинской помощи"),

    // === Негативные ===
    FRAGILE("fragile", Category.NEGATIVE, 8, "Хрупкий"),
    DISORGANIZED("disorganized", Category.NEGATIVE, 7, "Неорганизованный"),
    DIABETIC("diabetic", Category.NEGATIVE, 7, "Диабетик"),
    ANOREXIC("anorexic", Category.NEGATIVE, 6, "Анорексичка"),
    WEAKLING("weakling", Category.NEGATIVE, 6, "Задохлик"),
    HOMEBODY("homebody", Category.NEGATIVE, 5, "Домашний"),
    UNLUCKY("unlucky", Category.NEGATIVE, 5, "Неудачливый"),
    TURTLE("turtle", Category.NEGATIVE, 5, "Черепаха"),
    WIMP("wimp", Category.NEGATIVE, 5, "Слабак"),
    SUGGESTIBLE("suggestible", Category.NEGATIVE, 5, "Внушаемый"),
    CAFFEINE_ADDICTION("caffeine_addiction", Category.NEGATIVE, 5, "Зависимость от кофеина"),
    COWARD("coward", Category.NEGATIVE, 4, "Трусливый"),
    CLUMSY("clumsy", Category.NEGATIVE, 4, "Плохая координация"),
    TECHNOPHOBE("technophobe", Category.NEGATIVE, 4, "Технофоб"),
    LANDLUBBER("landlubber", Category.NEGATIVE, 3, "Сухопутный"),
    TEETOTALER("teetotaler", Category.NEGATIVE, 2, "Непьющий"),

    // === Служебные ===
    ADMIN("admin", Category.SERVICE, 0, "Админ");

    public enum Category {
        MAIN("main"),
        SKILL("skill"),
        NEGATIVE("negative"),
        SERVICE("service");

        private final String id;

        Category(String id) { this.id = id; }

        public String getId() { return id; }
        public String getTranslationKey() { return "category.rpperks." + id; }
    }

    private final String id;
    private final Category category;
    private final int defaultCost;
    private final String title;

    Perk(String id, Category category, int defaultCost, String title) {
        this.id = id;
        this.category = category;
        this.defaultCost = defaultCost;
        this.title = title;
    }

    public String getId() { return id; }
    public Category getCategory() { return category; }
    public int getDefaultCost() { return defaultCost; }
    /** Русское название — используется в комментариях конфига. */
    public String getTitle() { return title; }

    /** Актуальная цена из конфига (всегда положительное число). */
    public int getCost() { return Config.getCost(this); }

    /** Положительные перки и умения стоят баллы, негативные — дают. */
    public boolean isPositive() { return category != Category.NEGATIVE; }

    /** На сколько изменится баланс игрока при выдаче перка. */
    public int getPointsDelta() { return isPositive() ? -getCost() : getCost(); }

    public String getNameKey() { return "perk.rpperks." + id + ".name"; }
    public String getDescKey() { return "perk.rpperks." + id + ".desc"; }

    public static Optional<Perk> fromId(String id) {
        for (Perk perk : values()) {
            if (perk.id.equalsIgnoreCase(id)) return Optional.of(perk);
        }
        return Optional.empty();
    }
}
