package faygolover.rpstamina;

import net.minecraftforge.common.ForgeConfigSpec;

/** Клиентский конфиг: полоска выносливости. Положение можно менять прямо в игре командой /staminahud. */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    /** Якорь: угол/середина экрана, к которому «привязана» полоска. */
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

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue HIDE_WHEN_FULL;
    public static final ForgeConfigSpec.EnumValue<Anchor> ANCHOR;
    public static final ForgeConfigSpec.IntValue OFFSET_X;
    public static final ForgeConfigSpec.IntValue OFFSET_Y;
    public static final ForgeConfigSpec.IntValue WIDTH;
    public static final ForgeConfigSpec.IntValue HEIGHT;
    public static final ForgeConfigSpec.ConfigValue<String> COLOR_NORMAL;
    public static final ForgeConfigSpec.ConfigValue<String> COLOR_LOW;
    public static final ForgeConfigSpec.ConfigValue<String> COLOR_EXHAUSTED;

    public static final int DEFAULT_OFFSET_X = 0;
    public static final int DEFAULT_OFFSET_Y = -30;
    public static final int DEFAULT_WIDTH = 182;
    public static final int DEFAULT_HEIGHT = 3;

    static {
        B.push("hud");
        ENABLED = B.comment("Показывать полоску выносливости.").define("enabled", true);
        HIDE_WHEN_FULL = B.comment("Плавно скрывать полоску, когда выносливость полная.").define("hide_when_full", true);
        ANCHOR = B.comment("Якорь полоски на экране.").defineEnum("anchor", Anchor.BOTTOM_CENTER);
        OFFSET_X = B.comment("Смещение по X от якоря, пиксели GUI.").defineInRange("offset_x", DEFAULT_OFFSET_X, -4000, 4000);
        OFFSET_Y = B.comment("Смещение по Y от якоря, пиксели GUI (по умолчанию: чуть выше шкалы опыта).")
                .defineInRange("offset_y", DEFAULT_OFFSET_Y, -4000, 4000);
        WIDTH = B.comment("Ширина полоски.").defineInRange("width", DEFAULT_WIDTH, 10, 800);
        HEIGHT = B.comment("Высота полоски.").defineInRange("height", DEFAULT_HEIGHT, 1, 30);
        COLOR_NORMAL = B.comment("Цвет заполнения (#RRGGBB).").define("color_normal", "#D9B23A");
        COLOR_LOW = B.comment("Цвет при выносливости меньше 25% (#RRGGBB).").define("color_low", "#E07A24");
        COLOR_EXHAUSTED = B.comment("Цвет мигания в состоянии измотанности (#RRGGBB).").define("color_exhausted", "#C0392B");
        B.pop();
        SPEC = B.build();
    }

    /** Разбирает цвет вида #RRGGBB или RRGGBB; при ошибке возвращает запасной. */
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
