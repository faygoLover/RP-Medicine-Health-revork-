package faygolover.rpmedicine.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Клиентская часть предметов мода: 3D-модели (GeoItemRenderer) и хват (замечание 06.10: инструменты держать
 * как положено). Крупные вещи — двумя руками перед собой.
 */
public final class ItemPoses {
    private ItemPoses() {}

    private static final Set<String> TWO_HANDED = Set.of("defibrillator", "first_aid_kit", "organ_container", "ambu_bag",
            "stabilization_kit");

    private static HumanoidModel.ArmPose twoHands;

    /**
     * Создать позу при запуске клиента: позже нельзя — HumanoidModel уже построил таблицу поз,
     * и новая поза роняет игру (ArrayIndexOutOfBounds в poseRightArm).
     */
    public static void init() {
        if (twoHands != null) return;
        // Обе руки вперёд и чуть внутрь: несёт перед грудью.
        twoHands = HumanoidModel.ArmPose.create("RPMEDICINE_TWO_HANDS", true, (model, entity, arm) -> {
            model.rightArm.xRot = -0.85f + model.head.xRot * 0.3f;
            model.rightArm.yRot = -0.35f;
            model.leftArm.xRot = -0.85f + model.head.xRot * 0.3f;
            model.leftArm.yRot = 0.35f;
        });
    }

    public static boolean twoHanded(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(faygolover.rpmedicine.RpMedicine.MODID) && TWO_HANDED.contains(id.getPath());
    }

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return faygolover.rpmedicine.client.geo.GeoItemRenderer.get();
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return twoHands != null && twoHanded(stack) ? twoHands : null;
        }
    };
}
