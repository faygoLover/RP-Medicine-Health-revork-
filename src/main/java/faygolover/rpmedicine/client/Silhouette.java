package faygolover.rpmedicine.client;

import faygolover.rpmedicine.core.BodyPart;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/** Силуэт из девяти частей (вид спереди: правая сторона тела — слева на экране). Размеры в условных единицах 16×32. */
public final class Silhouette {
    private Silhouette() {}

    /** {x, y, w, h} в единицах сетки 16×32. */
    private static final int[][] RECTS = new int[9][];

    static {
        RECTS[BodyPart.HEAD.ordinal()] = new int[]{6, 0, 4, 4};
        RECTS[BodyPart.CHEST.ordinal()] = new int[]{5, 5, 6, 6};
        RECTS[BodyPart.ABDOMEN.ordinal()] = new int[]{5, 11, 6, 4};
        RECTS[BodyPart.RIGHT_ARM.ordinal()] = new int[]{2, 5, 3, 10};
        RECTS[BodyPart.LEFT_ARM.ordinal()] = new int[]{11, 5, 3, 10};
        RECTS[BodyPart.RIGHT_LEG.ordinal()] = new int[]{5, 15, 3, 13};
        RECTS[BodyPart.LEFT_LEG.ordinal()] = new int[]{8, 15, 3, 13};
        RECTS[BodyPart.RIGHT_FOOT.ordinal()] = new int[]{4, 28, 4, 2};
        RECTS[BodyPart.LEFT_FOOT.ordinal()] = new int[]{8, 28, 4, 2};
    }

    public static final int GRID_W = 16;
    public static final int GRID_H = 30;

    /** Цвет состояния 0–4. */
    public static int color(int c, boolean blink) {
        return switch (c) {
            case 0 -> 0xFF5E8C5A;
            case 1 -> 0xFFC9B84A;
            case 2 -> 0xFFD98634;
            case 3 -> 0xFFC8392E;
            default -> blink && (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFF5A0F0F : 0xFF8E1414;
        };
    }

    /** Рисует силуэт; {@code unit} — пикселей на единицу сетки. */
    public static void draw(GuiGraphics g, int x, int y, int unit, byte[] colors, @Nullable BodyPart selected, @Nullable BodyPart hover) {
        for (BodyPart p : BodyPart.VALUES) {
            int[] r = RECTS[p.ordinal()];
            int x0 = x + r[0] * unit;
            int y0 = y + r[1] * unit;
            int x1 = x0 + r[2] * unit - (unit > 2 ? 1 : 0);
            int y1 = y0 + r[3] * unit - (unit > 2 ? 1 : 0);
            int c = colors != null && p.ordinal() < colors.length ? colors[p.ordinal()] : 0;
            g.fill(x0, y0, x1, y1, color(c, true));
            if (p == selected) g.renderOutline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFFFFFFFF);
            else if (p == hover) g.renderOutline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, 0xFFAAAAAA);
        }
    }

    @Nullable
    public static BodyPart partAt(int x, int y, int unit, double mx, double my) {
        for (BodyPart p : BodyPart.VALUES) {
            int[] r = RECTS[p.ordinal()];
            int x0 = x + r[0] * unit;
            int y0 = y + r[1] * unit;
            if (mx >= x0 && mx < x0 + r[2] * unit && my >= y0 && my < y0 + r[3] * unit) return p;
        }
        return null;
    }
}
