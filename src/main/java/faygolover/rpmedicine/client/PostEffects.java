package faygolover.rpmedicine.client;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.List;

/**
 * Постэффект мода (shaders/post/medical.json): размытие и обесцвечивание с плавной сменой силы, а
 * под опиатами — «плывущие» цвета. Включается только когда нужен и не перебивает чужие постэффекты.
 */
public final class PostEffects {
    private PostEffects() {}

    private static final ResourceLocation CHAIN = new ResourceLocation(RpMedicine.MODID, "shaders/post/medical.json");
    private static boolean active;
    private static float saturation = 1f;
    private static float blur;
    private static float high;
    private static long lastNanos;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            active = false;
            return;
        }
        PostChain cur = mc.gameRenderer.currentEffect();
        boolean ours = cur != null && cur.getName().equals(CHAIN.toString());
        boolean need = targetSaturation() < 0.99f || targetBlur() > 0.2f || targetHigh() > 0.02f
                || saturation < 0.99f || blur > 0.2f || high > 0.02f;
        if (!need) {
            if (ours) mc.gameRenderer.shutdownEffect();
            active = false;
            return;
        }
        // Чужой постэффект (вид от лица моба и т.п.) не перебиваем.
        if (cur != null && !ours) return;
        if (!ours) {
            mc.gameRenderer.loadEffect(CHAIN);
            active = true;
        }
        // F4 выключает постэффекты ванили — наш нужен для состояния, включаем обратно.
        try {
            Boolean on = ObfuscationReflectionHelper.getPrivateValue(GameRenderer.class, mc.gameRenderer, "f_109053_");
            if (on != null && !on) ObfuscationReflectionHelper.setPrivateValue(GameRenderer.class, mc.gameRenderer, true, "f_109053_");
        } catch (Exception ignored) {
        }
    }

    private static float strength() {
        return (float) (double) ClientConfig.EFFECT_STRENGTH.get();
    }

    private static float targetSaturation() {
        SelfView v = ClientState.self;
        if (!ClientConfig.DAZED_GRAY.get()) return 1f;
        float gray = Math.min(1f, v.gray / 20f);
        if (v.isDown() && v.down != 3) gray = Math.max(gray, 0.85f);
        return 1f - Math.min(1f, gray * strength());
    }

    private static float targetBlur() {
        SelfView v = ClientState.self;
        if (!ClientConfig.PAIN_BLUR.get()) return 0f;
        return Math.max(0, (v.blur - 4) / 16f) * 10f * strength();
    }

    private static float targetHigh() {
        return Math.min(1f, ClientState.self.high / 20f) * strength();
    }

    /** Каждый кадр: плавно подводим силу к цели и выставляем параметры шейдера. */
    public static void frame() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float k = 1f - (float) Math.exp(-dt * 1.6f);
        saturation += (targetSaturation() - saturation) * k;
        blur += (targetBlur() - blur) * k;
        high += (targetHigh() - high) * k;
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        PostChain cur = mc.gameRenderer.currentEffect();
        if (cur == null || !cur.getName().equals(CHAIN.toString())) return;
        try {
            List<PostPass> passes = ObfuscationReflectionHelper.getPrivateValue(PostChain.class, cur, "f_110009_");
            if (passes == null || passes.size() < 3) return;
            float r = blur + high * 2.5f;
            passes.get(0).getEffect().safeGetUniform("Radius").set(r);
            passes.get(1).getEffect().safeGetUniform("Radius").set(r);
            var cc = passes.get(2).getEffect();
            float sat = saturation + high * 0.9f;
            cc.safeGetUniform("Saturation").set(sat);
            // Опиаты: цвета медленно плывут по кругу.
            double t = System.currentTimeMillis() / 1000.0;
            float a = (float) (high * 0.35 * Math.sin(t * 0.7));
            float b = (float) (high * 0.35 * Math.sin(t * 0.53 + 2.0));
            cc.safeGetUniform("RedMatrix").set(1f - Math.abs(a), a, -a * 0.5f);
            cc.safeGetUniform("GreenMatrix").set(-b * 0.5f, 1f - Math.abs(b), b);
            cc.safeGetUniform("BlueMatrix").set(a, -b, 1f - Math.abs(a + b) * 0.5f);
        } catch (Exception ignored) {
        }
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        PostChain cur = mc.gameRenderer.currentEffect();
        if (cur != null && cur.getName().equals(CHAIN.toString())) mc.gameRenderer.shutdownEffect();
        active = false;
        saturation = 1f;
        blur = 0f;
        high = 0f;
    }
}
