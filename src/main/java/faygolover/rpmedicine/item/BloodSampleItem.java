package faygolover.rpmedicine.item;

import faygolover.rpmedicine.server.LabService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Пробирка крови пациента для лабораторного стола. */
public class BloodSampleItem extends Item {
    public BloodSampleItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String name = LabService.patientName(stack);
        tooltip.add(Component.translatable("rpmedicine.tooltip.sample_of", name != null ? name : "?").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rpmedicine.blood_sample.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
