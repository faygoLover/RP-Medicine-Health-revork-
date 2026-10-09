package faygolover.rpmedicine.integration;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
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
 * Хирургические перчатки на руках (замечание 09.10, И37; модель — Ф31): слот Curios «руки». Своя модель — только кисть
 * (куб чуть шире руки, 5 px от кончиков пальцев), под широкую и тонкую руку. От третьего лица — рендерер Curios,
 * от первого — своя кисть поверх ванильной руки. Только клиент и только при установленном Curios.
 */
public final class CuriosClient {
    private CuriosClient() {}

    static final ResourceLocation TEX = new ResourceLocation(RpMedicine.MODID, "textures/models/surgical_gloves_worn.png");
    private static ModelPart wide, slim;

    /** Кисти: дети right_arm и left_arm — как у модели игрока (точки поворота те же). */
    private static ModelPart hands(boolean thin) {
        if (wide == null) {
            wide = bake(false);
            slim = bake(true);
        }
        return thin ? slim : wide;
    }

    private static ModelPart bake(boolean thin) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation inflate = new CubeDeformation(0.3f);
        float w = thin ? 3 : 4, y = thin ? 2.5f : 2;
        int v = thin ? 16 : 0;
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, v).addBox(thin ? -2 : -3, 5, -2, w, 5, 4, inflate),
                PartPose.offset(-5, y, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, v).addBox(-1, 5, -2, w, 5, 4, inflate),
                PartPose.offset(5, y, 0));
        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    private static boolean thin(LivingEntity e) {
        return e instanceof AbstractClientPlayer p && "slim".equals(p.getModelName());
    }

    public static void register() {
        CuriosRendererRegistry.register(ModItems.SURGICAL_GLOVES.get(), Gloves::new);
        MinecraftForge.EVENT_BUS.addListener(CuriosClient::onRenderArm);
    }

    static final class Gloves implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> void render(ItemStack stack, SlotContext ctx,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> body)) return;
            ModelPart m = hands(thin(ctx.entity()));
            var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
            ModelPart r = m.getChild("right_arm"), l = m.getChild("left_arm");
            r.copyFrom(body.rightArm);
            l.copyFrom(body.leftArm);
            r.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
            l.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** Рука от первого лица: перчатка поверх, если надета. */
    private static void onRenderArm(RenderArmEvent e) {
        if (CuriosCompat.find(e.getPlayer(), ModItems.SURGICAL_GLOVES.get()).isEmpty()) return;
        ModelPart arm = hands(thin(e.getPlayer())).getChild(e.getArm() == HumanoidArm.RIGHT ? "right_arm" : "left_arm");
        // Поза руки — как у ванильной руки игрока от первого лица.
        var playerModel = ((net.minecraft.client.renderer.entity.player.PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(e.getPlayer())).getModel();
        arm.copyFrom(e.getArm() == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm);
        arm.xRot = 0;
        arm.render(e.getPoseStack(), e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEX)), e.getPackedLight(), OverlayTexture.NO_OVERLAY);
    }
}
