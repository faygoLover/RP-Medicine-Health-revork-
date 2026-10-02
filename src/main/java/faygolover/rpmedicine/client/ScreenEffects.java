package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Экранные эффекты (п. 7.5 ТЗ): виньетка от боли, потемнение от низкого давления, сужение поля
 * зрения от кровопотери. Размытие и серость — постэффекты ({@link PostEffects}). Значения сглаживаются.
 */
public final class ScreenEffects {
    private ScreenEffects() {}

    private static float vignette;
    private static float darken;
    private static float tunnel;

    private static float f(byte v) {
        return Math.max(0, Math.min(1, v / 20f));
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isDeadOrDying()) return;
        SelfView v = ClientState.self;
        float tv = ClientConfig.PAIN_VIGNETTE.get() ? f(v.vignette) : 0;
        float td = ClientConfig.LOW_PRESSURE_DARKEN.get() ? f(v.darken) : 0;
        float tt = ClientConfig.BLOOD_LOSS_TUNNEL.get() ? f(v.tunnel) : 0;
        if (v.isDown() && v.down != 3) td = Math.max(td, 0.45f);
        vignette += (tv - vignette) * 0.05f;
        darken += (td - darken) * 0.05f;
        tunnel += (tt - tunnel) * 0.05f;

        if (darken > 0.01f) g.fill(0, 0, sw, sh, argb(darken * 0.75f, 0x000000));
        float edge = Math.max(tunnel, vignette * 0.8f);
        if (edge > 0.01f) {
            int color = tunnel >= vignette ? 0x000000 : 0x3A0000;
            edges(g, sw, sh, edge, color);
        }
    }

    /** Затемнение краёв полосами с убывающей прозрачностью. */
    private static void edges(GuiGraphics g, int sw, int sh, float strength, int rgb) {
        int depth = (int) (Math.min(sw, sh) * (0.18f + 0.3f * strength));
        int steps = Math.max(1, depth);
        int step = 1;
        for (int i = 0; i < steps; i++) {
            float k = 1f - (float) i / steps;
            float a = strength * 0.85f * k * k;
            int c = argb(a, rgb);
            int o = i * step;
            g.fill(o, o, sw - o, o + step, c);
            g.fill(o, sh - o - step, sw - o, sh - o, c);
            g.fill(o, o + step, o + step, sh - o - step, c);
            g.fill(sw - o - step, o + step, sw - o, sh - o - step, c);
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
