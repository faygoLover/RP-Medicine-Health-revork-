package faygolover.rpmedicine.client;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.network.RhythmPacket;
import faygolover.rpmedicine.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * СЛР и мешок Амбу в ритм (решения, п. 1.13): медик держит ПКМ на пациенте, а пробелом —
 * компрессии в такт метронома (около 110 в минуту) или сжимает мешок до зелёной зоны и отпускает.
 * Пока идёт действие, пробел не прыгает.
 */
@Mod.EventBusSubscriber(modid = RpMedicine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Rhythm {
    private Rhythm() {}

    /** 0 — нет, 1 — СЛР, 2 — Амбу. */
    static int mode;
    private static long start;
    /** Период компрессий, мс: ≈110 в минуту, у опытного чуть медленнее (замечание 18). */
    private static long cprPeriod = 545;
    /** Половина окна попадания в долях периода: новичку узко, опытному широко. */
    private static double window = 0.12;
    private static float lastQuality = -1;
    private static long lastBeat;
    private static int streak;
    // Амбу
    private static boolean squeezing;
    private static float squeeze;
    private static float peak;
    private static long lastFrame;
    private static long cooldownUntil;

    public static void set(String label, int level) {
        int lv = Math.max(0, Math.min(10, level));
        cprPeriod = 545 + 6L * lv;
        window = 0.10 + 0.025 * lv;
        int m = label.equals("rpmedicine.action.cpr") ? 1 : label.equals("rpmedicine.action.ambu") ? 2 : 0;
        if (m != mode) {
            mode = m;
            start = System.currentTimeMillis();
            lastQuality = -1;
            streak = 0;
            squeezing = false;
            squeeze = 0;
        }
    }

    public static void stop() {
        mode = 0;
        squeezing = false;
    }

    public static boolean active() {
        return mode != 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKey(InputEvent.Key e) {
        if (mode == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !mc.options.keyJump.matches(e.getKey(), e.getScanCode())) return;
        long now = System.currentTimeMillis();
        if (mode == 1 && e.getAction() == GLFW.GLFW_PRESS) {
            // Такт — когда точка проходит центр (раньше точка была у края в момент такта — замечание 5).
            double phase = ((now - start) % cprPeriod) / (double) cprPeriod;
            double dist = Math.min(phase, 1 - phase);
            float q = (float) Math.max(0, Math.min(1, 1 - dist / window));
            lastQuality = q;
            lastBeat = now;
            streak = q >= 0.5 ? streak + 1 : 0;
            Network.sendToServer(new RhythmPacket(false, q));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.MINIGAME_OK.get(), q >= 0.5 ? 1.4f : 0.7f, 0.25f));
        } else if (mode == 2) {
            if (e.getAction() == GLFW.GLFW_PRESS && now >= cooldownUntil) {
                squeezing = true;
                peak = 0;
            } else if (e.getAction() == GLFW.GLFW_RELEASE && squeezing) {
                squeezing = false;
                // Вдох хорош, если мешок сжали до зелёной зоны (0,65–0,9), но не до упора.
                float q = peak >= 0.65f && peak <= 0.9f ? 1f : peak > 0.9f ? 0.4f : Math.max(0, peak / 0.65f * 0.6f);
                lastQuality = q;
                lastBeat = now;
                Network.sendToServer(new RhythmPacket(true, q));
                cooldownUntil = now + 2500;
            }
        }
    }

    /** Пробел — для ритма, не для прыжка. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInput(MovementInputUpdateEvent e) {
        if (mode != 0) e.getInput().jumping = false;
    }

    /** Отрисовка вместо прогресс-бара (64×… в локальных координатах элемента PROGRESS). */
    public static void draw(GuiGraphics g, Font font) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        g.fill(-3, -3, 103, 34, 0x90000000);
        if (mode == 1) {
            g.drawString(font, Component.translatable("rpmedicine.rhythm.cpr").withStyle(ChatFormatting.WHITE), 0, 0, 0xFFFFFF, true);
            // Метроном: точка качается маятником и проходит центр ровно в такт (раз в период).
            double t = (now - start) / (double) cprPeriod;
            double phase = t % 1.0;
            double pos = 0.5 + 0.5 * Math.sin(Math.PI * t);
            g.fill(0, 14, 100, 18, 0xFF303030);
            // Зелёная зона — ширина окна попадания: за время window*период точка уходит от центра на столько.
            int half = Math.max(2, (int) Math.round(50 * Math.sin(Math.PI * window)));
            g.fill(50 - half, 12, 50 + half, 20, 0xFF2E8B57);
            int x = (int) Math.round(pos * 100) - 2;
            g.fill(x, 11, x + 4, 21, 0xFFFFFFFF);
            if (phase < 0.06 || phase > 0.94) g.fill(50 - half, 12, 50 + half, 20, 0xFF7CFF9C);
        } else {
            g.drawString(font, Component.translatable("rpmedicine.rhythm.ambu").withStyle(ChatFormatting.WHITE), 0, 0, 0xFFFFFF, true);
            if (squeezing) {
                squeeze = Math.min(1f, squeeze + dt * 0.9f);
                peak = Math.max(peak, squeeze);
            } else {
                squeeze = Math.max(0f, squeeze - dt * 1.4f);
            }
            g.fill(0, 14, 100, 18, 0xFF303030);
            g.fill(65, 12, 90, 20, 0xFF2E8B57);
            g.fill(90, 12, 100, 20, 0xFF7A2020);
            g.fill(0, 14, (int) (squeeze * 100), 18, 0xFF6090E0);
            if (now < cooldownUntil) g.drawString(font, Component.translatable("rpmedicine.rhythm.exhale").withStyle(ChatFormatting.GRAY), 0, 24, 0xAAAAAA, false);
        }
        if (lastQuality >= 0 && now - lastBeat < 900) {
            String k = lastQuality >= 0.8 ? "rpmedicine.rhythm.great" : lastQuality >= 0.5 ? "rpmedicine.rhythm.good" : "rpmedicine.rhythm.miss";
            int c = lastQuality >= 0.8 ? 0x7CFF9C : lastQuality >= 0.5 ? 0xE0E070 : 0xFF6060;
            Component t = Component.translatable(k);
            if (mode == 1 && streak > 2) t = Component.translatable(k).append(" ×" + streak);
            g.drawString(font, t, 100 - font.width(t), 24, c, true);
        } else if (mode == 1) {
            g.drawString(font, Component.translatable("rpmedicine.rhythm.space").withStyle(ChatFormatting.DARK_GRAY), 0, 24, 0x808080, false);
        }
    }
}
