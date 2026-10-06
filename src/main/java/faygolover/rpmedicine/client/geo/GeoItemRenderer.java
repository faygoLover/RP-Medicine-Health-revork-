package faygolover.rpmedicine.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import faygolover.rpmedicine.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Рисует предмет 3D-моделью из {@link GeoLibrary}. В руке у того, кто лечит, проигрывается анимация
 * применения — от начала прогресс-бара; в остальное время — анимация покоя, если есть.
 * В инвентаре предмет рисуется плоской иконкой (модель {@code forge:separate_transforms}).
 */
public final class GeoItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static GeoItemRenderer instance;

    public static GeoItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new GeoItemRenderer(mc);
        }
        return instance;
    }

    private GeoItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        GeoLibrary.Entry e = id == null ? null : GeoLibrary.get(id);
        if (e == null) return;
        GeoAnim.Clip clip = null;
        float t = 0;
        boolean firstPerson = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (firstPerson && ClientState.progressActive() && ClientState.progressTotal > 0
                && ItemStack.isSameItem(ClientState.progressIcon, stack)) {
            clip = e.clip(e.use());
            if (clip != null) {
                t = (System.currentTimeMillis() - ClientState.progressStart) / 1000f;
                t = clip.loop && clip.length > 0 ? t % clip.length : Math.min(t, clip.length);
            }
        }
        if (clip == null) {
            clip = e.clip(e.idle());
            if (clip != null && clip.length > 0) t = (System.currentTimeMillis() % 100000L) / 1000f % clip.length;
        }
        pose.pushPose();
        GeoModel m = e.model();
        if (e.fit()) {
            float[] b = e.bounds();
            float max = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
            float k = max <= 0 ? 1 : e.size() / max;
            pose.translate(0.5, 0.5, 0.5);
            pose.scale(k, k, k);
            pose.translate(-(b[0] + b[3]) / 2, -(b[1] + b[4]) / 2, -(b[2] + b[5]) / 2);
        } else {
            // Как GeckoLib: начало координат модели — центр низа блока.
            pose.translate(0.5, 0.51, 0.5);
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        }
        // Шприц-ручка: окошко заполнено по оставшимся дозам (0, 1, 2, 3, 4 использовано — округление вверх).
        fill = -1;
        if (faygolover.rpmedicine.registry.ModItems.isPen(stack)) {
            double used = 4 - faygolover.rpmedicine.registry.ModItems.remainingDoses(stack);
            fill = (float) (4 - Math.min(4, Math.ceil(used - 1e-6))) / 4f;
        } else if (stack.is(faygolover.rpmedicine.registry.ModItems.USED_PEN.get())) {
            fill = 0;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(e.texture()));
        for (GeoModel.Bone bone : m.roots) renderBone(bone, e, clip, t, pose, vc, light, overlay);
        pose.popPose();
    }

    /** Нарисовать модель целиком (блоки госпиталя): начало координат — центр низа блока. */
    public static void renderModel(GeoLibrary.Entry e, @Nullable GeoAnim.Clip clip, float t, PoseStack pose, VertexConsumer vc, int light, int overlay) {
        fill = -1;
        for (GeoModel.Bone bone : e.model().roots) renderBone(bone, e, clip, t, pose, vc, light, overlay);
    }

    /** Руки игрока вместо кубов *_pos (вид от первого лица, FirstPersonGeo). */
    public interface ArmHook {
        void draw(GeoModel.Bone bone, PoseStack pose, int light);
    }

    @Nullable
    public static ArmHook armHook;

    /** Нарисовать модель с руками игрока (вид от первого лица). */
    public static void renderWithArms(GeoLibrary.Entry e, @Nullable GeoAnim.Clip clip, float t, PoseStack pose, VertexConsumer vc, int light,
                                      int overlay, ArmHook hook) {
        armHook = hook;
        try {
            renderModel(e, clip, t, pose, vc, light, overlay);
        } finally {
            armHook = null;
        }
    }

    /** Заполнение ручки 0–1 для текущего предмета (-1 — не ручка). */
    private static float fill = -1;
    /** Высота жидкости в окошке ручки при полной, единицы модели. */
    private static final float PEN_LIQUID = 6.8f;

    private static void renderBone(GeoModel.Bone b, GeoLibrary.Entry e, @Nullable GeoAnim.Clip clip, float t, PoseStack pose,
                                   VertexConsumer vc, int light, int overlay) {
        if (e.hide().contains(b.name) && !(armHook != null && b.name.contains("hand"))) return;
        if (fill >= 0 && b.name.equals("liquid") && fill <= 0.001f) return;
        pose.pushPose();
        if (fill >= 0 && b.name.equals("stopper")) pose.translate(0, PEN_LIQUID * fill, 0);
        if (fill >= 0 && b.name.equals("liquid")) {
            pose.translate(b.px, b.py, b.pz);
            pose.scale(1, fill, 1);
            pose.translate(-b.px, -b.py, -b.pz);
        }
        float[] pos = clip == null ? null : clip.sample(b.name, 1, t);
        if (pos != null) pose.translate(-pos[0], pos[1], pos[2]);
        pose.translate(b.px, b.py, b.pz);
        float rx = b.rx, ry = b.ry, rz = b.rz;
        float[] rot = clip == null ? null : clip.sample(b.name, 0, t);
        if (rot != null) {
            rx += (float) Math.toRadians(-rot[0]);
            ry += (float) Math.toRadians(-rot[1]);
            rz += (float) Math.toRadians(rot[2]);
        }
        if (rz != 0) pose.mulPose(Axis.ZP.rotation(rz));
        if (ry != 0) pose.mulPose(Axis.YP.rotation(ry));
        if (rx != 0) pose.mulPose(Axis.XP.rotation(rx));
        float[] sc = clip == null ? null : clip.sample(b.name, 2, t);
        if (sc != null) pose.scale(nz(sc[0]), nz(sc[1]), nz(sc[2]));
        pose.translate(-b.px, -b.py, -b.pz);
        if (armHook != null && (b.name.equals("lefthand_pos") || b.name.equals("righthand_pos"))) {
            armHook.draw(b, pose, light);
        } else {
            quads(b.quads, pose, vc, light, overlay);
        }
        for (GeoModel.RotatedGroup g : b.rotated) {
            pose.pushPose();
            pose.translate(g.px(), g.py(), g.pz());
            if (g.rz() != 0) pose.mulPose(Axis.ZP.rotation(g.rz()));
            if (g.ry() != 0) pose.mulPose(Axis.YP.rotation(g.ry()));
            if (g.rx() != 0) pose.mulPose(Axis.XP.rotation(g.rx()));
            pose.translate(-g.px(), -g.py(), -g.pz());
            quads(g.quads(), pose, vc, light, overlay);
            pose.popPose();
        }
        for (GeoModel.Bone c : b.children) renderBone(c, e, clip, t, pose, vc, light, overlay);
        pose.popPose();
    }

    private static float nz(float v) {
        return Math.abs(v) < 1e-4f ? 1e-4f : v;
    }

    private static void quads(java.util.List<GeoModel.Quad> qs, PoseStack pose, VertexConsumer vc, int light, int overlay) {
        if (qs.isEmpty()) return;
        Matrix4f mat = pose.last().pose();
        Matrix3f nor = pose.last().normal();
        for (GeoModel.Quad q : qs) {
            for (int i = 0; i < 4; i++) {
                float[] p = q.pos()[i];
                float[] uv = q.uv()[i];
                vc.vertex(mat, p[0], p[1], p[2]).color(1f, 1f, 1f, 1f).uv(uv[0], uv[1]).overlayCoords(overlay).uv2(light)
                        .normal(nor, q.nx(), q.ny(), q.nz()).endVertex();
            }
        }
    }
}
