package faygolover.rpmedicine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.client.ClientState;
import faygolover.rpmedicine.hospital.IvStandBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Стойка капельницы: пакеты на крючках (модели Industrial Hellscape, перекрашены: физраствор, кровь,
 * пустые после них) и шланг к руке пациента — провисает, как поводок (замечание 35).
 */
public class IvStandRenderer implements BlockEntityRenderer<IvStandBlockEntity> {
    private static final String[] KINDS = {null, "saline", "blood", "empty_saline", "empty_blood", "saline_yellow", "saline_milky"};
    /** Низ каждого пакета в координатах модели (для стойки лицом на север), откуда идёт шланг. */
    private static final Vec3[] HOOK = {new Vec3(8, 19, 4), new Vec3(12, 19, 8), new Vec3(3.75, 19, 8.25), new Vec3(8, 19, 12)};

    public IvStandRenderer(BlockEntityRendererProvider.Context ctx) {}

    public static ResourceLocation model(int hook, String kind) {
        return new ResourceLocation(RpMedicine.MODID, "block/iv_bag_" + hook + "_" + kind);
    }

    public static List<ResourceLocation> allModels() {
        List<ResourceLocation> out = new java.util.ArrayList<>();
        for (int i = 0; i < IvStandBlockEntity.HOOKS; i++)
            for (int k = 1; k < KINDS.length; k++) out.add(model(i, KINDS[k]));
        return out;
    }

    @Override
    public void render(IvStandBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var mc = Minecraft.getInstance();
        var renderer = mc.getBlockRenderer().getModelRenderer();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        int hoseFrom = -1;
        for (int i = 0; i < IvStandBlockEntity.HOOKS; i++) {
            byte k = be.clientKinds[i];
            if (k <= 0 || k >= KINDS.length) continue;
            BakedModel m = mc.getModelManager().getModel(model(i, KINDS[k]));
            renderer.renderModel(pose.last(), vc, be.getBlockState(), m, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.translucent());
            if (hoseFrom < 0 && k != IvStandBlockEntity.EMPTY_SALINE && k != IvStandBlockEntity.EMPTY_BLOOD) hoseFrom = i;
        }
        pose.popPose();
        if (be.clientPatientId >= 0 && mc.level != null) {
            Entity e = mc.level.getEntity(be.clientPatientId);
            if (e instanceof LivingEntity patient) {
                // Шланг — от того пакета, что сейчас капает (опустел — переходит к следующему).
                int from = be.clientActive >= 0 ? be.clientActive : hoseFrom >= 0 ? hoseFrom : 0;
                boolean blood = be.clientKinds[from] == IvStandBlockEntity.BLOOD;
                Vec3 local = HOOK[from].scale(1 / 16.0).subtract(0.5, 0, 0.5);
                local = local.yRot((float) Math.toRadians(facing.toYRot() + 180) * -1f + 0f);
                Vec3 start = Vec3.atLowerCornerOf(be.getBlockPos()).add(0.5, 0, 0.5).add(local);
                Vec3 end = armPos(patient, be.clientArm, pt);
                hose(pose, buffers, be.getBlockPos(), start, end, blood);
            }
        }
    }

    /** Где катетер: кисть нужной руки (у лежачего — у тела). */
    private static Vec3 armPos(LivingEntity p, int arm, float pt) {
        Vec3 base = p.getPosition(pt);
        boolean left = arm == faygolover.rpmedicine.core.BodyPart.LEFT_ARM.ordinal();
        double side = left ? 0.36 : -0.36;
        // Лежачий и на койке: тело лежит вдоль взгляда, руки вдоль тела — кисть у бедра, сбоку.
        if (p instanceof net.minecraft.world.entity.player.Player pl && DownedPose.isDowned(pl)) {
            float yaw = DownedPose.lyingYaw(pl) * Mth.DEG_TO_RAD;
            return base.add(Mth.cos(yaw) * side, 0.32, Mth.sin(yaw) * side);
        }
        float yaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        return base.add(Mth.cos(yaw) * side, 0.78, Mth.sin(yaw) * side);
    }

    /** Шланг: провисающая трубка между точками, как поводок (две перекрещенные ленты). */
    private static void hose(PoseStack pose, MultiBufferSource buffers, BlockPos origin, Vec3 a, Vec3 b, boolean blood) {
        double len = faygolover.rpmedicine.core.MedicalSettings.get().ivHoseLength;
        double dist = a.distanceTo(b);
        double sag = Math.min(0.45, Math.max(0.05, (len - dist) * 0.15));
        Matrix4f mat = pose.last().pose();
        Vec3 o = Vec3.atLowerCornerOf(origin);
        int r = blood ? 150 : 225, g = blood ? 30 : 230, bl = blood ? 40 : 235;
        int segs = 24;
        float w = 0.028f;
        for (int pass = 0; pass < 2; pass++) {
            // Лента — triangle strip: каждый проход отдельной партией.
            VertexConsumer vc = buffers.getBuffer(RenderType.leash());
            for (int i = 0; i <= segs; i++) {
                double t = i / (double) segs;
                Vec3 p = a.lerp(b, t).add(0, -sag * 4 * t * (1 - t), 0).subtract(o);
                int light = lightAt(a.lerp(b, t));
                float shade = (i % 2 == 0) ? 1f : 0.85f;
                int cr = (int) (r * shade), cg = (int) (g * shade), cb = (int) (bl * shade);
                if (pass == 0) {
                    vc.vertex(mat, (float) p.x, (float) p.y + w, (float) p.z).color(cr, cg, cb, 255).uv2(light).endVertex();
                    vc.vertex(mat, (float) p.x, (float) p.y - w, (float) p.z).color(cr, cg, cb, 255).uv2(light).endVertex();
                } else {
                    vc.vertex(mat, (float) p.x - w, (float) p.y, (float) p.z - w).color(cr, cg, cb, 255).uv2(light).endVertex();
                    vc.vertex(mat, (float) p.x + w, (float) p.y, (float) p.z + w).color(cr, cg, cb, 255).uv2(light).endVertex();
                }
            }
        }
    }

    private static int lightAt(Vec3 v) {
        var level = Minecraft.getInstance().level;
        if (level == null) return LightTexture.FULL_BRIGHT;
        BlockPos p = BlockPos.containing(v);
        return LightTexture.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
    }

    @Override
    public boolean shouldRenderOffScreen(IvStandBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
