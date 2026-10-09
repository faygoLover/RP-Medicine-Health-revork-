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
    /** Открывается с анимацией (аптечка LR: расстегнуть, сунуть руку). */
    private final boolean animated;

    public MedicalContainerItem(Properties props, int slots, boolean animated) {
        super(props);
        this.slots = slots;
        this.animated = animated;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // Аптечка: сначала анимация — расстегнуть молнию (LR Tactical), потом открыть (замечание 06.10).
            if (animated && hand == InteractionHand.MAIN_HAND) {
                if (!(faygolover.rpmedicine.server.ActionManager.current(sp) instanceof OpenKit))
                    faygolover.rpmedicine.server.ActionManager.start(new OpenKit(sp, stack.copy(), this));
            } else {
                open(sp, hand, stack);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    void open(ServerPlayer sp, InteractionHand hand, ItemStack stack) {
        int slot = hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : -1;
        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> new MedicalContainerMenu(id, inv, hand, slots),
                stack.getHoverName()), buf -> {
            buf.writeEnum(hand);
            buf.writeVarInt(slots);
            buf.writeVarInt(slot);
        });
    }

    /** Открыть аптечку: время на анимацию молнии. */
    static final class OpenKit extends faygolover.rpmedicine.server.ActionManager.TimedAction {
        private final ItemStack original;
        private final MedicalContainerItem item;

        OpenKit(ServerPlayer sp, ItemStack original, MedicalContainerItem item) {
            // Окно — когда рука по анимации уже внутри аптечки (замечание 10.10, Ф1: было 38 тиков).
            super(sp, 24);
            this.original = original;
            this.item = item;
        }

        @Override
        public String label() {
            return "rpmedicine.action.open_kit";
        }

        @Override
        public boolean slowsActor() {
            return false;
        }

        @Override
        public ItemStack icon() {
            return original;
        }

        @Override
        public String checkContinue() {
            return actor.getMainHandItem().getItem() == original.getItem() ? null : "rpmedicine.action.item_changed";
        }

        @Override
        public void complete() {
            item.open(actor, InteractionHand.MAIN_HAND, actor.getMainHandItem());
        }
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
