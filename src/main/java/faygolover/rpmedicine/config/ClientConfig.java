package faygolover.rpmedicine.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;

/**
 * Клиентский конфиг (п. 11 ТЗ): эффекты экрана и звука, положение элементов HUD.
 * Положение HUD меняется прямо в игре: {@code /rpmedicine hud}.
 */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    /** Якорь: угол или середина экрана, к которой привязан элемент. */
    public enum Anchor {
        TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
        CENTER_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), CENTER_RIGHT(1f, 0.5f),
        BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

        public final float fx;
        public final float fy;

        Anchor(float fx, float fy) {
            this.fx = fx;
            this.fy = fy;
        }

        public Anchor next() {
            Anchor[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** Элементы HUD (п. 7.4 ТЗ) и их положение по умолчанию. */
    public enum HudElement {
        SILHOUETTE("silhouette", Anchor.BOTTOM_LEFT, 6, -54, 1.0),
        STATUS("status", Anchor.BOTTOM_LEFT, 44, -22, 1.0),
        PROGRESS("progress", Anchor.CENTER, -50, 18, 1.0),
        KNOCKDOWN_TIMER("knockdown_timer", Anchor.TOP_CENTER, 0, 30, 1.0),
        HOVER("hover", Anchor.CENTER, 77, 0, 1.0),
        MONITOR("monitor", Anchor.CENTER, 67, 0, 1.0),
        /** Общее состояние вместо ванильных сердец; запас (золотое яблоко) — золотом поверх. */
        HEALTH("health", Anchor.BOTTOM_CENTER, -51, -30, 1.0);

        public final String id;
        public final Anchor defAnchor;
        public final int defX;
        public final int defY;
        public final double defScale;

        HudElement(String id, Anchor a, int x, int y, double scale) {
            this.id = id;
            this.defAnchor = a;
            this.defX = x;
            this.defY = y;
            this.defScale = scale;
        }
    }

    public static final class ElementConfig {
        public final ForgeConfigSpec.BooleanValue visible;
        public final ForgeConfigSpec.EnumValue<Anchor> anchor;
        public final ForgeConfigSpec.IntValue offsetX;
        public final ForgeConfigSpec.IntValue offsetY;
        public final ForgeConfigSpec.DoubleValue scale;

        ElementConfig(HudElement e) {
            B.push(e.id);
            visible = B.comment("Показывать.").define("visible", true);
            anchor = B.comment("Якорь на экране.").defineEnum("anchor", e.defAnchor);
            offsetX = B.comment("Смещение по X от якоря, пиксели GUI.").defineInRange("offset_x", e.defX, -4000, 4000);
            offsetY = B.comment("Смещение по Y от якоря, пиксели GUI.").defineInRange("offset_y", e.defY, -4000, 4000);
            scale = B.comment("Масштаб.").defineInRange("scale", e.defScale, 0.25, 4.0);
            B.pop();
        }

        public void reset(HudElement e) {
            visible.set(true);
            anchor.set(e.defAnchor);
            offsetX.set(e.defX);
            offsetY.set(e.defY);
            scale.set(e.defScale);
        }
    }

    public static final EnumMap<HudElement, ElementConfig> HUD = new EnumMap<>(HudElement.class);

    public static final ForgeConfigSpec.BooleanValue HIDE_VANILLA_HEALTH;
    public static final ForgeConfigSpec.BooleanValue PAIN_VIGNETTE;
    public static final ForgeConfigSpec.BooleanValue PAIN_BLUR;
    public static final ForgeConfigSpec.BooleanValue LOW_PRESSURE_DARKEN;
    public static final ForgeConfigSpec.BooleanValue BLOOD_LOSS_TUNNEL;
    public static final ForgeConfigSpec.BooleanValue DAZED_GRAY;
    public static final ForgeConfigSpec.BooleanValue MUFFLED_SOUND;
    public static final ForgeConfigSpec.BooleanValue CONCUSSION_RINGING;
    public static final ForgeConfigSpec.BooleanValue HEARTBEAT;
    public static final ForgeConfigSpec.BooleanValue HEAVY_BREATHING;
    public static final ForgeConfigSpec.BooleanValue AIM_SWAY;
    public static final ForgeConfigSpec.BooleanValue LOOK_UP_WHEN_DOWNED;
    public static final ForgeConfigSpec.BooleanValue SENSATION_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue LEAVE_BODY;
    public static final ForgeConfigSpec.BooleanValue NO_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue SHOW_MISSING_LIMBS;
    public static final ForgeConfigSpec.DoubleValue HEARTBEAT_VOLUME;
    public static final ForgeConfigSpec.DoubleValue BREATHING_VOLUME;
    public static final ForgeConfigSpec.DoubleValue RINGING_VOLUME;
    public static final ForgeConfigSpec.DoubleValue EFFECT_STRENGTH;

    static {
        B.comment("RP Medicine — клиентский конфиг.").push("hud");
        HIDE_VANILLA_HEALTH = B.comment("Скрывать ванильную полоску сердец (здоровье живёт в травмах).").define("hide_vanilla_health", true);
        for (HudElement e : HudElement.values()) HUD.put(e, new ElementConfig(e));
        B.pop();

        B.comment("Эффекты экрана и звука (п. 7.5 ТЗ). Каждый можно отключить.").push("effects");
        PAIN_VIGNETTE = B.comment("Виньетка от боли.").define("pain_vignette", true);
        PAIN_BLUR = B.comment("Размытие от боли и контузии.").define("pain_blur", true);
        LOW_PRESSURE_DARKEN = B.comment("Потемнение от низкого давления.").define("low_pressure_darken", true);
        BLOOD_LOSS_TUNNEL = B.comment("Сужение поля зрения от кровопотери.").define("blood_loss_tunnel", true);
        DAZED_GRAY = B.comment("Серость при оглушении.").define("dazed_gray", true);
        MUFFLED_SOUND = B.comment("Глухой звук при оглушении.").define("muffled_sound", true);
        CONCUSSION_RINGING = B.comment("Звон в ушах после контузии.").define("concussion_ringing", true);
        HEARTBEAT = B.comment("Стук сердца.").define("heartbeat", true);
        HEAVY_BREATHING = B.comment("Тяжёлое дыхание.").define("heavy_breathing", true);
        AIM_SWAY = B.comment("Дрожь и раскачка прицела.").define("aim_sway", true);
        LOOK_UP_WHEN_DOWNED = B.comment("Лежачий от первого лица смотрит в небо.").define("look_up_when_downed", true);
        SENSATION_MESSAGES = B.comment("Ощущения текстом над панелью быстрого доступа.").define("sensation_messages", true);
        LEAVE_BODY = B.comment("Оставлять тело при выходе из игры в обмороке, нокдауне или на койке (если выключить — тело уходит с вами).")
                .define("leave_body", true);
        NO_MINIGAMES = B.comment("Без мини-игр: лечить всегда прогресс-баром (если сервер разрешает отказ от мини-игр).").define("no_minigames", false);
        SHOW_MISSING_LIMBS = B.comment("Не рисовать отсутствующие (ампутированные) конечности на модели игрока.").define("show_missing_limbs", true);
        EFFECT_STRENGTH = B.comment("Сила эффектов экрана (0–1,5).").defineInRange("effect_strength", 1.0, 0.0, 1.5);
        B.pop();

        B.comment("Громкость звуков состояния (0 — выключено).").push("volume");
        HEARTBEAT_VOLUME = B.comment("Стук сердца.").defineInRange("heartbeat", 0.6, 0.0, 1.0);
        BREATHING_VOLUME = B.comment("Тяжёлое дыхание.").defineInRange("breathing", 0.6, 0.0, 1.0);
        RINGING_VOLUME = B.comment("Звон в ушах.").defineInRange("ringing", 0.6, 0.0, 1.0);
        B.pop();
        SPEC = B.build();
    }

    public static ElementConfig hud(HudElement e) {
        return HUD.get(e);
    }

    /** Разбирает цвет вида #RRGGBB; при ошибке возвращает запасной. */
    public static int parseColor(String text, int fallback) {
        try {
            String s = text.trim();
            if (s.startsWith("#")) s = s.substring(1);
            return 0xFF000000 | (Integer.parseInt(s, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return fallback;
        }
    }

    private ClientConfig() {}
}
