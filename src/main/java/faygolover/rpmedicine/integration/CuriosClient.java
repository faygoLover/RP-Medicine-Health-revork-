package faygolover.rpmedicine.integration;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.common.MinecraftForge;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Хирургические перчатки на руках (замечание 09.10, И37): слот Curios «руки», вид — рукава брони Self Expression
 * (голубые, только кисти). От третьего лица — рендерер Curios, от первого — своя рука поверх ванильной.
 * Только клиент и только при установленном Curios.
 */
public final class CuriosClient {
    private CuriosClient() {}

    static final ResourceLocation TEX = new ResourceLocation(RpMedicine.MODID, "textures/models/surgical_gloves_worn.png");
    private static HumanoidModel<LivingEntity> model;

    private static HumanoidModel<LivingEntity> model() {
        if (model == null) model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        return model;
    }

    public static void register() {
        CuriosRendererRegistry.register(ModItems.SURGICAL_GLOVES.get(), Gloves::new);
        MinecraftForge.EVENT_BUS.addListener(CuriosClient::onRenderArm);
    }

    static final class Gloves implements ICurioRenderer {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> void render(ItemStack stack, SlotContext ctx,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> body)) return;
            HumanoidModel<LivingEntity> m = model();
            ((HumanoidModel<LivingEntity>) body).copyPropertiesTo(m);
            m.setAllVisible(false);
            m.rightArm.visible = true;
            m.leftArm.visible = true;
            m.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }

    /** Рука от первого лица: перчатка поверх, если надета. */
    private static void onRenderArm(RenderArmEvent e) {
        if (CuriosCompat.find(e.getPlayer(), ModItems.SURGICAL_GLOVES.get()).isEmpty()) return;
        HumanoidModel<LivingEntity> m = model();
        ModelPart arm = e.getArm() == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
        // Поза руки — как у ванильной руки игрока от первого лица.
        var playerModel = ((net.minecraft.client.renderer.entity.player.PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(e.getPlayer())).getModel();
        ModelPart src = e.getArm() == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm;
        arm.copyFrom(src);
        arm.xRot = 0;
        arm.visible = true;
        arm.render(e.getPoseStack(), e.getMultiBufferSource().getBuffer(RenderType.armorCutoutNoCull(TEX)), e.getPackedLight(), OverlayTexture.NO_OVERLAY);
    }
}
