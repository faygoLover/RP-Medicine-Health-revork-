package faygolover.rpmedicine.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.network.MedcardPhotoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Снимок своего скина для медкарты (замечание 09.10, М2): сервер просит — клиент ждёт, пока скин догрузится
 * (с Ely.by он приходит не сразу), снимает текстуру с видеокарты в PNG и отправляет. Скин так и не загрузился
 * за полминуты — ничего не шлём: сервер попросит снова при следующем входе.
 */
public final class SkinPhoto {
    private SkinPhoto() {}

    /** Сколько тиков ещё ждать скин (0 — запроса нет). */
    private static int waitTicks;
    /** Скин уже не по умолчанию: подождать ещё немного, пока текстура точно загружена. */
    private static int settleTicks;

    public static void request() {
        waitTicks = 20 * 30;
        settleTicks = 40;
    }

    /** Каждый тик клиента (поток отрисовки). */
    public static void tick() {
        if (waitTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            waitTicks = 0;
            return;
        }
        waitTicks--;
        ResourceLocation skin = mc.player.getSkinTextureLocation();
        if (skin.equals(DefaultPlayerSkin.getDefaultSkin(mc.player.getUUID()))) return;
        if (settleTicks-- > 0) return;
        waitTicks = 0;
        byte[] png = capture(mc, skin);
        if (png != null && png.length <= MedcardPhotoPacket.MAX_BYTES)
            faygolover.rpmedicine.network.Network.sendToServer(new MedcardPhotoPacket.Photo(png));
    }

    private static byte[] capture(Minecraft mc, ResourceLocation loc) {
        var tex = mc.getTextureManager().getTexture(loc);
        try {
            RenderSystem.bindTexture(tex.getId());
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w < 64 || h < 32 || w > 512 || h > 512) return null;
            try (NativeImage img = new NativeImage(w, h, false)) {
                img.downloadTexture(0, false);
                return img.asByteArray();
            }
        } catch (Exception e) {
            RpMedicine.LOGGER.warn("RP Medicine: не снять скин для медкарты: {}", e.toString());
            return null;
        }
    }
}
