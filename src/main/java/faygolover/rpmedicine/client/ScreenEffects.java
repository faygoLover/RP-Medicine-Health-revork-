package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Экранные эффекты (п. 7.5 ТЗ): виньетка от боли, потемнение от низкого давления, сужение поля
 * зрения от кровопотери и веки: при потере сознания глаза медленно моргают и закрываются, потом
 * приоткрываются в серый мир; в полуобморочном состоянии — редкие медленные моргания.
 * Серость и размытие — постэффект ({@link PostEffects}). Всё сглаживается по времени.
 */
public final class ScreenEffects {
    private ScreenEffects() {}

    private static float vignette;
    private static float darken;
    private static float tunnel;

    // Веки: 0 — открыты, 1 — закрыты.
    private static float lids;
    private static int lastDown;
    /** Начало текущей анимации век, мс; тип: 1 — засыпание, 2 — пробуждение, 3 — моргание. */
    private static long animStart;
    private static int anim;
    private static long nextBlink;
    private static long lastNanos;

    private static float f(byte v) {
        return Math.max(0, Math.min(1, v / 20f));
    }

    /** Засыпание: два тяжёлых моргания, глаза закрываются, потом приоткрываются в серый мир. */
    private static float fallAsleep(float t) {
        if (t < 0.45f) return ease(t / 0.45f) * 0.6f;
        if (t < 0.7f) return 0.6f - ease((t - 0.45f) / 0.25f) * 0.3f;
        if (t < 1.15f) return 0.3f + ease((t - 0.7f) / 0.45f) * 0.6f;
        if (t < 1.4f) return 0.9f - ease((t - 1.15f) / 0.25f) * 0.25f;
        if (t < 1.9f) return 0.65f + ease((t - 1.4f) / 0.5f) * 0.35f;
        if (t < 2.6f) return 1f;
        if (t < 3.8f) return 1f - ease((t - 2.6f) / 1.2f) * 0.62f;
        return 0.38f;
    }

    private static float wakeUp(float t) {
        if (t < 0.6f) return 0.38f - ease(t / 0.6f) * 0.38f;
        if (t < 0.8f) return ease((t - 0.6f) / 0.2f) * 0.5f;
        if (t < 1.2f) return 0.5f - ease((t - 0.8f) / 0.4f) * 0.5f;
        return 0f;
    }

    private static float blink(float t) {
        if (t < 0.35f) return ease(t / 0.35f) * 0.8f;
        if (t < 0.75f) return 0.8f - ease((t - 0.35f) / 0.4f) * 0.8f;
        return 0f;
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static void updateLids(SelfView v) {
        long now = System.currentTimeMillis();
        int down = v.down;
        if (down != lastDown) {
            if (lastDown == 0 && down != 0) {
                anim = 1;
                animStart = now;
            } else if (lastDown != 0 && down == 0) {
                anim = 2;
                animStart = now;
            }
            lastDown = down;
        }
        float t = (now - animStart) / 1000f;
        if (down == 3) {
            lids = 1f;
            return;
        }
        if (anim == 1) {
            lids = fallAsleep(t);
            if (t > 3.8f) anim = 0;
            return;
        }
        if (anim == 2) {
            lids = wakeUp(t);
            if (t > 1.2f) anim = 0;
            return;
        }
        if (down != 0) {
            // Лежит: глаза приоткрыты, веки подрагивают.
            lids = 0.38f + (float) Math.sin(now / 700.0) * 0.03f;
            return;
        }
        // В полуобмороке — медленные моргания раз в 4–7 секунд.
        boolean drowsy = v.gray >= 4 || v.darken >= 8 || v.sedated;
        if (anim == 3) {
            lids = blink(t);
            if (t > 0.75f) anim = 0;
        } else {
            lids = 0f;
            if (drowsy && now > nextBlink) {
                anim = 3;
                animStart = now;
                nextBlink = now + 4000 + (long) (Math.random() * 3000);
            }
        }
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isDeadOrDying()) return;
        SelfView v = ClientState.self;
        float strength = (float) (double) ClientConfig.EFFECT_STRENGTH.get();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float k = 1f - (float) Math.exp(-dt * 2.5f);

        float tv = ClientConfig.PAIN_VIGNETTE.get() ? f(v.vignette) : 0;
        float td = ClientConfig.LOW_PRESSURE_DARKEN.get() ? f(v.darken) : 0;
        float tt = ClientConfig.BLOOD_LOSS_TUNNEL.get() ? f(v.tunnel) : 0;
        // Лежачий видит, пусть и тускло: мир серый, чуть темнее.
        if (v.isDown() && v.down != 3) td = Math.max(td, 0.22f);
        vignette += (tv - vignette) * k;
        darken += (td - darken) * k;
        tunnel += (tt - tunnel) * k;
        updateLids(v);

        if (darken > 0.01f) g.fill(0, 0, sw, sh, argb(Math.min(0.85f, darken * 0.6f * strength), 0x000000));
        float edge = Math.max(tunnel, vignette * 0.8f) * strength;
        if (edge > 0.01f) {
            int color = tunnel >= vignette ? 0x000000 : 0x3A0000;
            edges(g, sw, sh, Math.min(1f, edge), color);
        }
        if (lids > 0.005f) drawLids(g, sw, sh, lids);
    }

    /** Веки: тёмные полосы сверху и снизу с мягким краем, слегка подрагивают. */
    private static void drawLids(GuiGraphics g, int sw, int sh, float amount) {
        int half = sh / 2;
        float jitter = (float) Math.sin(System.currentTimeMillis() / 90.0) * 1.5f * amount;
        int h = (int) (half * Math.min(1f, amount) + jitter);
        if (amount >= 0.999f) {
            g.fill(0, 0, sw, sh, 0xFF000000);
            return;
        }
        g.fill(0, 0, sw, h, 0xFF000000);
        g.fill(0, sh - h, sw, sh, 0xFF000000);
        int soft = Math.max(4, sh / 14);
        for (int i = 0; i < soft; i++) {
            float a = 1f - (float) i / soft;
            int c = argb(a * a, 0x000000);
            g.fill(0, h + i, sw, h + i + 1, c);
            g.fill(0, sh - h - i - 1, sw, sh - h - i, c);
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
