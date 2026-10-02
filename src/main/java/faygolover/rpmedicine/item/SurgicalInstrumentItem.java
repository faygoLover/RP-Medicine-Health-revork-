package faygolover.rpmedicine.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Хирургический инструмент (пинцет): новый стерилен, после работы в ране — нет, пока его не
 * обработают в стерилизаторе (функция {@code sterilizer} датапака). Нестерильный — инфекция ×1,5.
 */
public class SurgicalInstrumentItem extends MedicalItem {
    private static final String USED = "Used";

    public SurgicalInstrumentItem(Properties props) {
        super(props);
    }

    public static boolean isSterile(ItemStack stack) {
        return !stack.hasTag() || !stack.getTag().getBoolean(USED);
    }

    public static void setSterile(ItemStack stack, boolean sterile) {
        if (sterile) {
            if (stack.hasTag()) {
                stack.getTag().remove(USED);
                if (stack.getTag().isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().putBoolean(USED, true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(isSterile(stack)
                ? Component.translatable("rpmedicine.tooltip.sterile").withStyle(ChatFormatting.AQUA)
                : Component.translatable("rpmedicine.tooltip.not_sterile").withStyle(ChatFormatting.GOLD));
    }
}
