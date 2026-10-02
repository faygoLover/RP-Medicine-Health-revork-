package faygolover.rpmedicine.client;

import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.network.SelfView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Постэффекты: серость при оглушении (ванильный шейдер desaturate) и размытие от боли и контузии
 * (ванильный blur). Включаем только свой эффект и не трогаем чужие.
 */
public final class PostEffects {
    private PostEffects() {}

    private static final ResourceLocation GRAY = new ResourceLocation("shaders/post/desaturate.json");
    private static final ResourceLocation BLUR = new ResourceLocation("shaders/post/blur.json");
    @Nullable
    private static ResourceLocation active;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            active = null;
            return;
        }
        SelfView v = ClientState.self;
        ResourceLocation want = null;
        if (ClientConfig.DAZED_GRAY.get() && (v.gray >= 4 || (v.isDown() && v.down != 3))) want = GRAY;
        else if (ClientConfig.PAIN_BLUR.get() && v.blur >= 6) want = BLUR;
        PostChain cur = mc.gameRenderer.currentEffect();
        if (want == null) {
            if (active != null) {
                if (cur != null && cur.getName().equals(active.toString())) mc.gameRenderer.shutdownEffect();
                active = null;
            }
            return;
        }
        if (want.equals(active) && cur != null && cur.getName().equals(want.toString())) return;
        // Чужой постэффект (например, вид от лица моба) не перебиваем.
        if (cur != null && (active == null || !cur.getName().equals(active.toString()))) return;
        mc.gameRenderer.loadEffect(want);
        active = want;
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        PostChain cur = mc.gameRenderer.currentEffect();
        if (active != null && cur != null && cur.getName().equals(active.toString())) mc.gameRenderer.shutdownEffect();
        active = null;
    }
}
