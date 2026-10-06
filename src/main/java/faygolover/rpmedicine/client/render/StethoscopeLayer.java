package faygolover.rpmedicine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Стетоскоп в руке (замечание 06.10): оливы в ушах, трубки сходятся под подбородком и идут к головке в руке.
 * Сама головка — 3D-модель предмета в руке.
 */
public class StethoscopeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int TUBE = 0xFF2E2E34;
    private static final int METAL = 0xFFC8CDD4;

    public StethoscopeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbAmount,
                       float pt, float age, float headYaw, float headPitch) {
        boolean main = p.getMainHandItem().is(ModItems.STETHOSCOPE.get());
        boolean off = !main && p.getOffhandItem().is(ModItems.STETHOSCOPE.get());
        if (!main && !off || p.isInvisible()) return;
        boolean rightHand = (p.getMainArm() == HumanoidArm.RIGHT) == main;
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        if (!m.head.visible) return;
        Vector3f earL = point(pose, m.head, 4.4f, -3.4f, 0.3f);
        Vector3f earR = point(pose, m.head, -4.4f, -3.4f, 0.3f);
        Vector3f join = point(pose, m.body, 0f, 3.2f, -2.6f);
        ModelPart arm = rightHand ? m.rightArm : m.leftArm;
        Vector3f hand = point(pose, arm, rightHand ? -1f : 1f, 10.6f, -0.6f);
        // Низ мира в координатах вида (модель перевёрнута: «вниз» — +Y модели).
        Vector4f d = new Vector4f(0, 1, 0, 0).mul(pose.last().pose());
        Vector3f down = new Vector3f(d.x, d.y, d.z).normalize();
        float w = 0.022f;
        tube(buffers, earL, join, down, 0.06f, w, light);
        tube(buffers, earR, join, down, 0.06f, w, light);
        tube(buffers, join, hand, down, 0.12f, w * 1.2f, light);
        // Оливы — светлые точки у ушей.
        tube(buffers, earL, new Vector3f(earL).add(new Vector3f(down).mul(-0.045f)), down, 0, 0.03f, light, METAL);
        tube(buffers, earR, new Vector3f(earR).add(new Vector3f(down).mul(-0.045f)), down, 0, 0.03f, light, METAL);
    }

    private static Vector3f point(PoseStack pose, ModelPart part, float x, float y, float z) {
        pose.pushPose();
        part.translateAndRotate(pose);
        Vector4f v = new Vector4f(x / 16f, y / 16f, z / 16f, 1f).mul(pose.last().pose());
        pose.popPose();
        return new Vector3f(v.x, v.y, v.z);
    }

    private static void tube(MultiBufferSource buffers, Vector3f a, Vector3f b, Vector3f down, float sag, float w, int light) {
        tube(buffers, a, b, down, sag, w, light, TUBE);
    }

    /** Трубка — лента, повёрнутая к камере, с провисом вниз. Точки уже в координатах вида. */
    private static void tube(MultiBufferSource buffers, Vector3f a, Vector3f b, Vector3f down, float sag, float w, int light, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        Matrix4f id = new Matrix4f();
        int segs = 12;
        int r = argb >> 16 & 255, g = argb >> 8 & 255, bl = argb & 255;
        Vector3f prev = null;
        for (int i = 0; i <= segs; i++) {
            float t = i / (float) segs;
            Vector3f p = new Vector3f(a).lerp(b, t).add(new Vector3f(down).mul(sag * 4 * t * (1 - t)));
            Vector3f next = new Vector3f(a).lerp(b, Math.min(1, t + 1f / segs)).add(new Vector3f(down).mul(sag * 4 * Math.min(1, t + 1f / segs) * (1 - Math.min(1, t + 1f / segs))));
            Vector3f dir = i < segs ? new Vector3f(next).sub(p) : new Vector3f(p).sub(prev);
            // Ширина поперёк трубки и взгляда (камера в начале координат).
            Vector3f side = new Vector3f(dir).cross(new Vector3f(p).negate());
            if (side.lengthSquared() < 1e-8f) side.set(w, 0, 0);
            else side.normalize(w);
            float shade = i % 2 == 0 ? 1f : 0.88f;
            vc.vertex(id, p.x + side.x, p.y + side.y, p.z + side.z).color((int) (r * shade), (int) (g * shade), (int) (bl * shade), 255).uv2(light).endVertex();
            vc.vertex(id, p.x - side.x, p.y - side.y, p.z - side.z).color((int) (r * shade), (int) (g * shade), (int) (bl * shade), 255).uv2(light).endVertex();
            prev = p;
        }
    }
}
