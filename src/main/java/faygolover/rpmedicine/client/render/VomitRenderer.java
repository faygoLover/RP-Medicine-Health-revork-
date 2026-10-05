package faygolover.rpmedicine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.entity.VomitEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Лужа рвоты: плоская картинка на земле, к концу жизни бледнеет. */
public class VomitRenderer extends EntityRenderer<VomitEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(RpMedicine.MODID, "textures/entity/vomit.png");

    public VomitRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VomitEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.translate(0, 0.01, 0);
        ps.mulPose(Axis.YP.rotationDegrees((e.getId() * 47) % 360));
        float size = 0.45f + (e.getId() % 5) * 0.06f;
        float age = Math.min(1f, (e.tickCount + pt) / VomitEntity.LIFETIME);
        int alpha = (int) (230 * (1f - Math.max(0, age - 0.8f) / 0.2f));
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        vertex(vc, m, n, -size, -size, 0, 0, light, alpha);
        vertex(vc, m, n, -size, size, 0, 1, light, alpha);
        vertex(vc, m, n, size, size, 1, 1, light, alpha);
        vertex(vc, m, n, size, -size, 1, 0, light, alpha);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v, int light, int alpha) {
        vc.vertex(m, x, 0, z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(VomitEntity e) {
        return TEX;
    }
}
