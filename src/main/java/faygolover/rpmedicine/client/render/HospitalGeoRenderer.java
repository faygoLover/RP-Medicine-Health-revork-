package faygolover.rpmedicine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import faygolover.rpmedicine.client.geo.GeoAnim;
import faygolover.rpmedicine.client.geo.GeoItemRenderer;
import faygolover.rpmedicine.client.geo.GeoLibrary;
import faygolover.rpmedicine.hospital.HospitalGeoBlock;
import faygolover.rpmedicine.hospital.HospitalStorageBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Блоки Health & Disease (JEDIGD, MIT) своим 3D-рендером: шкаф и ящик открываются, когда в них смотрят,
 * стерилизатор — когда им пользуются, стол-лаборатория крутит свою анимацию.
 */
public class HospitalGeoRenderer implements BlockEntityRenderer<HospitalStorageBlockEntity> {
    public HospitalGeoRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(HospitalStorageBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.kind() == HospitalGeoBlock.Kind.THERMOSTAT) return;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        GeoLibrary.Entry e = id == null ? null : GeoLibrary.get(id);
        if (e == null) return;
        long now = System.currentTimeMillis();
        GeoAnim.Clip clip = null;
        float t = 0;
        if (be.kind() == HospitalGeoBlock.Kind.LAB_TABLE) {
            clip = e.clip("0");
            if (clip != null && clip.length > 0) t = (now % 100000L) / 1000f % clip.length;
        } else if (be.clientOpenedAt > 0 || be.clientClosedAt > 0) {
            boolean open = be.clientOpenedAt > be.clientClosedAt;
            clip = e.clip(open ? "1" : "2");
            if (clip != null) t = Math.min(clip.length, (now - (open ? be.clientOpenedAt : be.clientClosedAt)) / 1000f);
        }
        Direction f = be.getBlockState().getValue(HospitalGeoBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Перед модели (+z) — туда, куда смотрит блок (к игроку, который его ставил).
        pose.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        GeoItemRenderer.renderModel(e, clip, t, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(e.texture())), light, overlay);
        pose.popPose();
    }
}
