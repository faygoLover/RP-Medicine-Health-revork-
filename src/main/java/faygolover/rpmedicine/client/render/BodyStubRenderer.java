package faygolover.rpmedicine.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import faygolover.rpmedicine.entity.BodyStubEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Тело офлайн-игрока: модель игрока со скином владельца и бронёй, лёжа на спине.
 * Не проверено визуально — нужна проверка автором.
 */
public class BodyStubRenderer extends LivingEntityRenderer<BodyStubEntity, PlayerModel<BodyStubEntity>> {
    private static final Map<UUID, ResourceLocation> SKINS = new HashMap<>();

    public BodyStubRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(BodyStubEntity e) {
        ResourceLocation cached = SKINS.get(e.ownerId());
        if (cached != null) return cached;
        ResourceLocation rl = DefaultPlayerSkin.getDefaultSkin(e.ownerId());
        if (e.skinValue() != null) {
            GameProfile profile = new GameProfile(e.ownerId(), e.ownerName());
            profile.getProperties().put("textures", new Property("textures", e.skinValue(), e.skinSignature()));
            var skins = Minecraft.getInstance().getSkinManager().getInsecureSkinInformation(profile);
            MinecraftProfileTexture tex = skins.get(MinecraftProfileTexture.Type.SKIN);
            if (tex != null) rl = Minecraft.getInstance().getSkinManager().registerTexture(tex, MinecraftProfileTexture.Type.SKIN);
        }
        SKINS.put(e.ownerId(), rl);
        return rl;
    }

    @Override
    protected void setupRotations(BodyStubEntity e, PoseStack pose, float ageInTicks, float rotationYaw, float partialTicks) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - rotationYaw));
        // Лёжа на спине, центр тела — в точке сущности (так считает место попадания).
        pose.translate(0, 0.15, -0.9);
        pose.mulPose(Axis.XP.rotationDegrees(90.0f));
    }

    @Override
    protected boolean shouldShowName(BodyStubEntity e) {
        return false;
    }
}
