package faygolover.rpmedicine.item;

import faygolover.rpmedicine.menu.MedicalContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Подсумок и аптечка: контейнер только для медицинских предметов (п. 6.2 ТЗ). */
public class MedicalContainerItem extends Item {
    public final int slots;

    public MedicalContainerItem(Properties props, int slots) {
        super(props);
        this.slots = slots;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            int slot = hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : -1;
            NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                    (id, inv, p) -> new MedicalContainerMenu(id, inv, hand, slots),
                    stack.getHoverName()), buf -> {
                buf.writeEnum(hand);
                buf.writeVarInt(slots);
                buf.writeVarInt(slot);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("rpmedicine.tooltip.container", slots).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /** 3D-модель в руке (если для предмета она есть в rpgeo/items.json) — {@code GeoItemRenderer}. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        // 3D-модели и хват двумя руками — клиентский класс.
        consumer.accept(faygolover.rpmedicine.client.ItemPoses.EXTENSIONS);
    }
}
