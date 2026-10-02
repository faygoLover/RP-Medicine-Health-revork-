package faygolover.rpmedicine.item;

import faygolover.rpmedicine.medcard.MedcardService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Медкарта (ТЗ второго этапа, п. 10). Пустая: ПКМ по игроку — привязать к нему, ПКМ в воздух — к себе.
 * Привязанная: ПКМ — открыть. Копий сколько угодно, все показывают одну карту.
 */
public class MedcardItem extends Item {
    public MedcardItem(Properties props) {
        super(props);
    }

    @Nullable
    public static UUID owner(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.hasUUID("Owner") ? t.getUUID("Owner") : null;
    }

    public static String ownerName(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null ? t.getString("OwnerName") : "";
    }

    /** Привязать: из стопки пустых карт отделяется одна. */
    private static ItemStack bind(Player player, InteractionHand hand, ItemStack stack, UUID uuid, String name) {
        ItemStack card = stack.split(1);
        card.getOrCreateTag().putUUID("Owner", uuid);
        card.getOrCreateTag().putString("OwnerName", name);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, card);
        } else if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }
        player.displayClientMessage(Component.translatable("rpmedicine.medcard.bound", name), true);
        return card;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        UUID owner = owner(stack);
        if (owner == null) {
            bind(player, hand, stack, player.getUUID(), player.getGameProfile().getName());
        } else if (player instanceof ServerPlayer sp) {
            MedcardService.open(sp, owner);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        UUID uuid = MedcardService.ownerOf(target);
        if (uuid == null) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (owner(stack) == null) {
            String name = target instanceof faygolover.rpmedicine.entity.BodyStubEntity stub ? stub.ownerName() : target.getName().getString();
            bind(player, hand, stack, uuid, name);
        } else if (player instanceof ServerPlayer sp) {
            MedcardService.open(sp, owner(stack));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (owner(stack) == null) tooltip.add(Component.translatable("rpmedicine.medcard.blank").withStyle(ChatFormatting.GRAY));
        else tooltip.add(Component.translatable("rpmedicine.medcard.of", ownerName(stack)).withStyle(ChatFormatting.AQUA));
    }
}
