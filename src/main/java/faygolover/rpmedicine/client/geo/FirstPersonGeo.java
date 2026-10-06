package faygolover.rpmedicine.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Вид от первого лица для моделей LR Tactical (как в TaCZ, замечание 06.10): модель ставится от своей кости
 * камеры ({@code idle_view}), на местах {@code lefthand_pos}/{@code righthand_pos} — руки игрока с его скином.
 * Анимации модели: «достать» при смене предмета, «применить» во время прогресса, иначе покой.
 */
@Mod.EventBusSubscriber(modid = RpMedicine.MODID, value = Dist.CLIENT)
public final class FirstPersonGeo {
    private FirstPersonGeo() {}

    private static ItemStack lastStack = ItemStack.EMPTY;
    private static long drawnAt;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack main = mc.player.getMainHandItem();
        GeoLibrary.Entry entry = entry(main);
        if (entry == null) {
            // Крупные вещи — двумя руками, как карту (замечание 06.10).
            if (faygolover.rpmedicine.client.ItemPoses.twoHanded(main)) {
                e.setCanceled(true);
                if (e.getHand() == InteractionHand.MAIN_HAND)
                    twoHands(main, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), e.getEquipProgress(), e.getSwingProgress(), mc);
            }
            return;
        }
        // Модель с обеими руками: вторую руку не рисуем.
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        render(entry, main, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), mc.player);
    }

    /** Обе руки держат предмет перед собой (по образцу ванильной карты в двух руках). */
    private static void twoHands(ItemStack st, PoseStack pose, MultiBufferSource buffers, int light, float equip, float swing, Minecraft mc) {
        var player = mc.player;
        var renderer = (net.minecraft.client.renderer.entity.player.PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        float sq = net.minecraft.util.Mth.sqrt(swing);
        pose.pushPose();
        pose.translate(0, 0.2f * net.minecraft.util.Mth.sin(swing * (float) Math.PI) / 2f, -0.4f * net.minecraft.util.Mth.sin(sq * (float) Math.PI));
        float tilt = mapTilt(player.getXRot());
        pose.translate(0, 0.04f + equip * -1.2f + tilt * -0.5f, -0.72f);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tilt * -85f));
        if (!player.isInvisible()) {
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
            hand(pose, buffers, light, renderer, player, true);
            hand(pose, buffers, light, renderer, player, false);
            pose.popPose();
        }
        // Предмет — между руками, лицом к игроку.
        pose.translate(0, -0.12f, -0.12f);
        pose.scale(0.55f, 0.55f, 0.55f);
        mc.getItemRenderer().renderStatic(st, net.minecraft.world.item.ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                player.level(), player.getId());
        pose.popPose();
    }

    private static void hand(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.renderer.entity.player.PlayerRenderer r,
                             AbstractClientPlayer player, boolean right) {
        float f = right ? 1f : -1f;
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(92f));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(45f));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f * -41f));
        pose.translate(f * 0.3f, -1.1f, 0.45f);
        if (right) r.renderRightHand(pose, buffers, light, player);
        else r.renderLeftHand(pose, buffers, light, player);
        pose.popPose();
    }

    private static float mapTilt(float xRot) {
        float f = 1f - xRot / 45f + 0.1f;
        f = net.minecraft.util.Mth.clamp(f, 0f, 1f);
        return -net.minecraft.util.Mth.cos(f * (float) Math.PI) * 0.5f + 0.5f;
    }

    /** Модель LR (есть камера и места под руки), иначе null. */
    private static GeoLibrary.Entry entry(ItemStack st) {
        if (st.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        GeoLibrary.Entry e = id == null ? null : GeoLibrary.get(id);
        if (e == null || !e.model().bones.containsKey("idle_view") || !e.model().bones.containsKey("righthand_pos")) return null;
        return e;
    }

    private static void render(GeoLibrary.Entry e, ItemStack st, PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player) {
        long now = System.currentTimeMillis();
        if (!ItemStack.isSameItem(st, lastStack)) {
            lastStack = st.copy();
            drawnAt = now;
        }
        GeoAnim.Clip clip = null;
        float t = 0;
        if (ClientState.progressActive() && ClientState.progressTotal > 0 && ItemStack.isSameItem(ClientState.progressIcon, st)) {
            clip = e.clip("use");
            if (clip != null) t = Math.min(clip.length, (now - ClientState.progressStart) / 1000f);
        }
        if (clip == null) {
            GeoAnim.Clip draw = e.clip("draw");
            float since = (now - drawnAt) / 1000f;
            if (draw != null && since < draw.length) {
                clip = draw;
                t = since;
            } else {
                clip = e.clip("static_idle");
                if (clip == null) clip = e.clip("idle");
                if (clip != null && clip.length > 0) t = (now % 100000L) / 1000f % clip.length;
            }
        }
        GeoModel.Bone cam = e.model().bones.get("idle_view");
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // Камера модели — в глаз игрока.
        pose.translate(-cam.px, -cam.py, -cam.pz);
        ResourceLocation skin = player.getSkinTextureLocation();
        boolean slim = "slim".equals(player.getModelName());
        VertexConsumer skinVc = buffers.getBuffer(RenderType.entitySolid(skin));
        GeoItemRenderer.renderWithArms(e, clip, t, pose, buffers.getBuffer(RenderType.entityTranslucent(e.texture())), light,
                OverlayTexture.NO_OVERLAY, (bone, ps, lt) -> arm(bone, ps, skinVc, lt, bone.name.startsWith("right"), slim));
        pose.popPose();
    }

    /** Рука игрока по кубу кости: развёртка скина как у ванильной руки (4×12×4, тонкая — 3 в ширину). */
    private static void arm(GeoModel.Bone bone, PoseStack pose, VertexConsumer vc, int light, boolean right, boolean slim) {
        for (float[] b : bone.boxes) {
            float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
            if (slim) {
                // Тонкая рука уже на 1 пиксель — со стороны большого пальца.
                if (right) x0 += 1;
                else x1 -= 1;
            }
            int w = slim ? 3 : 4;
            float u = right ? 40 : 32, v = right ? 16 : 48;
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            // Верх руки (плечо) — y1, кисть — y0.
            face(vc, m, n, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u + 4, v, u + 4 + w, v + 4, 0, 1, 0);          // верх
            face(vc, m, n, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, u + 4 + w, v, u + 4 + 2 * w, v + 4, 0, -1, 0); // низ (кисть)
            face(vc, m, n, light, x0, y1, z0, x0, y0, z0, x1, y0, z0, x1, y1, z0, u + 4, v + 4, u + 4 + w, v + 16, 0, 0, -1);  // перед
            face(vc, m, n, light, x1, y1, z1, x1, y0, z1, x0, y0, z1, x0, y1, z1, u + 8 + w, v + 4, u + 8 + 2 * w, v + 16, 0, 0, 1); // зад
            face(vc, m, n, light, x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1, u, v + 4, u + 4, v + 16, 1, 0, 0);           // бок
            face(vc, m, n, light, x0, y1, z1, x0, y0, z1, x0, y0, z0, x0, y1, z0, u + 4 + w, v + 4, u + 8 + w, v + 16, -1, 0, 0); // бок
        }
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1,
                             float nx, float ny, float nz) {
        float s = 64f;
        vtx(vc, m, n, light, ax, ay, az, u0 / s, v0 / s, nx, ny, nz);
        vtx(vc, m, n, light, bx, by, bz, u0 / s, v1 / s, nx, ny, nz);
        vtx(vc, m, n, light, cx, cy, cz, u1 / s, v1 / s, nx, ny, nz);
        vtx(vc, m, n, light, dx, dy, dz, u1 / s, v0 / s, nx, ny, nz);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x, float y, float z, float u, float v,
                            float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(1f, 1f, 1f, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
