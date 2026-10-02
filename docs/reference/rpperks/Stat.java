package faygolover.rpperks.perk;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Характеристики, которыми управляет мод.
 * База для всех игроков задаётся в конфиге ([baseline]), перки добавляют к ней свои изменения.
 * <p>
 * Характеристики выносливости (STAMINA_*) — атрибуты мода rpstamina. Они подключены по id,
 * без зависимости при компиляции: если rpstamina не установлен, эти характеристики просто пропускаются.
 */
public enum Stat {
    MAX_HEALTH("max_health", "Максимальное здоровье (2.0 = одно сердце)", "20.0",
            20.0, 1.0, 1024.0, () -> Attributes.MAX_HEALTH),
    MOVEMENT_SPEED("movement_speed", "Скорость ходьбы", "0.1",
            0.075, 0.001, 1.0, () -> Attributes.MOVEMENT_SPEED),
    ARMOR("armor", "Броня (2.0 = один щит на панели)", "0.0",
            0.0, 0.0, 30.0, () -> Attributes.ARMOR),
    ARMOR_TOUGHNESS("armor_toughness", "Твёрдость брони", "0.0",
            0.0, 0.0, 20.0, () -> Attributes.ARMOR_TOUGHNESS),
    KNOCKBACK_RESISTANCE("knockback_resistance", "Сопротивление отбрасыванию (0.0–1.0, 1.0 = не отбрасывает)", "0.0",
            0.0, 0.0, 1.0, () -> Attributes.KNOCKBACK_RESISTANCE),
    ATTACK_DAMAGE("attack_damage", "Урон в ближнем бою без оружия", "1.0",
            0.2, 0.0, 2048.0, () -> Attributes.ATTACK_DAMAGE),
    ATTACK_SPEED("attack_speed", "Скорость атаки (полностью заряженных ударов в секунду)", "4.0",
            3.5, 0.0, 1024.0, () -> Attributes.ATTACK_SPEED),
    MELEE_KNOCKBACK("melee_knockback_percent", "Сила отбрасывания от ударов в ближнем бою, % (0 = как в ванили, -100 = не отбрасывает)", "0.0",
            0.0, -100.0, 1000.0, Kind.CUSTOM),
    LUCK("luck", "Удача (лут, рыбалка)", "0.0",
            0.0, -1024.0, 1024.0, () -> Attributes.LUCK),
    BLOCK_REACH("block_reach", "Дальность взаимодействия с блоками", "4.5",
            2.0, 0.0, 1024.0, () -> ForgeMod.BLOCK_REACH.get()),
    ENTITY_REACH("entity_reach", "Дальность атаки и взаимодействия с существами", "3.0",
            3.0, 0.0, 1024.0, () -> ForgeMod.ENTITY_REACH.get()),
    STEP_HEIGHT("step_height", "Прибавка к высоте шага (ванильный шаг 0.6 блока; 1.0 => 1.6 блока, 1.5 => 2.1 блока)", "0.0",
            1.0, -1.0, 10.0, () -> ForgeMod.STEP_HEIGHT_ADDITION.get()),
    SWIM_SPEED("swim_speed", "Скорость плавания (множитель)", "1.0",
            0.6, 0.0, 1024.0, () -> ForgeMod.SWIM_SPEED.get()),

    // ---- rpstamina ----
    STAMINA_MAX("stamina_max", "RP Stamina: максимальная выносливость", "1000",
            1000.0, 1.0, 100000.0, "rpstamina", "max_stamina"),
    STAMINA_REGEN("stamina_regen_multiplier", "RP Stamina: множитель восстановления (1.0 = обычное, 1.25 = на 25% быстрее)", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "regen_multiplier"),
    STAMINA_COST("stamina_cost_multiplier", "RP Stamina: общий множитель расхода (1.0 = обычный, 0.85 = на 15% меньше)", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "cost_multiplier"),
    STAMINA_MOVE_COST("stamina_move_cost_multiplier", "RP Stamina: множитель расхода на бег, прыжки и лестницы", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "move_cost_multiplier"),
    STAMINA_SWIM_COST("stamina_swim_cost_multiplier", "RP Stamina: множитель расхода на плавание", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "swim_cost_multiplier"),
    STAMINA_MINE_COST("stamina_mine_cost_multiplier", "RP Stamina: множитель расхода на добычу блоков", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "mine_cost_multiplier"),
    STAMINA_MELEE_COST("stamina_melee_cost_multiplier", "RP Stamina: множитель расхода на удары и размахи в ближнем бою", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "melee_cost_multiplier"),
    STAMINA_ARMOR_BURDEN("stamina_armor_burden", "RP Stamina: влияние веса брони на расход движения (1.0 = обычно, 0 = броня не мешает)", "1.0",
            1.0, 0.0, 10.0, "rpstamina", "armor_burden"),

    THERMAL_RESISTANCE("thermal_resistance", "Legendary Survival Overhaul: терморезистентность", "значение LSO",
            2.0, -100.0, 100.0, "legendarysurvivaloverhaul", "thermal_resistance"),
    INVENTORY_SLOTS("inventory_slots", "Доступные слоты инвентаря (через команду inventory_slots_command)", "36",
            18.0, 0.0, 100.0, Kind.COMMAND);

    public enum Kind {
        /** Атрибут сущности (ванильный, Forge или стороннего мода). */
        ATTRIBUTE,
        /** Значение, которое применяется консольной командой другого мода. */
        COMMAND,
        /** Значение, которое мод обрабатывает сам в событиях. */
        CUSTOM
    }

    private final String key;
    private final String title;
    private final String vanilla;
    private final double defaultBase;
    private final double min;
    private final double max;
    private final Kind kind;
    @Nullable private final Supplier<Attribute> attribute;
    @Nullable private final ResourceLocation externalId;
    @Nullable private volatile Attribute externalCache;

    Stat(String key, String title, String vanilla, double defaultBase, double min, double max, Supplier<Attribute> attribute) {
        this(key, title, vanilla, defaultBase, min, max, Kind.ATTRIBUTE, attribute, null);
    }

    Stat(String key, String title, String vanilla, double defaultBase, double min, double max, String namespace, String path) {
        this(key, title, vanilla, defaultBase, min, max, Kind.ATTRIBUTE, null, new ResourceLocation(namespace, path));
    }

    Stat(String key, String title, String vanilla, double defaultBase, double min, double max, Kind kind) {
        this(key, title, vanilla, defaultBase, min, max, kind, null, null);
    }

    Stat(String key, String title, String vanilla, double defaultBase, double min, double max, Kind kind,
         @Nullable Supplier<Attribute> attribute, @Nullable ResourceLocation externalId) {
        this.key = key;
        this.title = title;
        this.vanilla = vanilla;
        this.defaultBase = defaultBase;
        this.min = min;
        this.max = max;
        this.kind = kind;
        this.attribute = attribute;
        this.externalId = externalId;
    }

    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getVanilla() { return vanilla; }
    public double getDefaultBase() { return defaultBase; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public Kind getKind() { return kind; }

    /** Бонусы перков к скорости задаются в процентах от базы, остальные — прибавкой. */
    public boolean isPercentBonus() { return this == MOVEMENT_SPEED; }

    /** Атрибут или null, если это не атрибут либо нужный мод не установлен. */
    @Nullable
    public Attribute getAttribute() {
        if (attribute != null) return attribute.get();
        if (externalId != null) {
            Attribute cached = externalCache;
            if (cached == null) {
                cached = ForgeRegistries.ATTRIBUTES.getValue(externalId);
                externalCache = cached;
            }
            return cached;
        }
        return null;
    }
}
