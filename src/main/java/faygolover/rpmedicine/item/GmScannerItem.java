package faygolover.rpmedicine.item;

import faygolover.rpmedicine.server.MedicalReports;
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

/** ГМ-сканер: показывает всё цифрами, без проверок (п. 6.2 ТЗ). Работает только у операторов. */
public class GmScannerItem extends Item {
    public GmScannerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.hasPermissions(2)) MedicalReports.sendFull(sp, sp);
            else sp.displayClientMessage(Component.translatable("rpmedicine.scanner.no_permission"), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide && player instanceof ServerPlayer sp) {
            if (sp.hasPermissions(2)) MedicalReports.sendFull(sp, target);
            else sp.displayClientMessage(Component.translatable("rpmedicine.scanner.no_permission"), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rpmedicine.gm_scanner.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
