package faygolover.rpmedicine.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Растворы хлоргексидина (замечание 09.10, И9), кроме 0,05 % для ран — он остаётся антисептиком лечения:
 * 0,2 % — промыть руки, 0,5 % — обеззаразить кожу перед уколом или операцией (себе — ПКМ, другому — ПКМ по нему),
 * концентрат 20 % — разводят водой в верстаке. Пока действие только сообщение.
 */
public class ChlorhexidineItem extends MedicalItem {
    public enum Kind { HANDS, SKIN, CONCENTRATE }

    private final Kind kind;

    public ChlorhexidineItem(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (kind == Kind.CONCENTRATE) {
            if (!level.isClientSide)
                player.displayClientMessage(Component.translatable("rpmedicine.msg.chlorhexidine_dilute").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        apply(level, player, player, st, hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player player, LivingEntity target, InteractionHand hand) {
        if (kind != Kind.SKIN) return InteractionResult.PASS;
        apply(player.level(), player, target, st, hand);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private void apply(Level level, Player player, LivingEntity target, ItemStack st, InteractionHand hand) {
        if (level.isClientSide) return;
        String key = kind == Kind.HANDS ? "rpmedicine.msg.chlorhexidine_hands"
                : target == player ? "rpmedicine.msg.chlorhexidine_skin_self" : "rpmedicine.msg.chlorhexidine_skin";
        player.displayClientMessage(Component.translatable(key, target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.6f, 1.4f);
        if (level instanceof ServerLevel sl)
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, target.getX(), target.getY() + (kind == Kind.HANDS ? 1.0 : 1.2),
                    target.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
        if (!player.getAbilities().instabuild) st.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
    }
}
