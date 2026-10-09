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
public class BloodSampleItem extends Item implements Perishable.Item {
    public BloodSampleItem(Properties props) {
        super(props);
    }

    /** Испорчена: в тепле дольше срока (в термостате и холодильнике время стоит). */
    public static boolean spoiled(ItemStack st, long now) {
        return Perishable.spoiled(st, now, faygolover.rpmedicine.core.MedicalSettings.get().sampleSpoilWarmHours, Perishable.NO_LIMIT);
    }

    @Override
    public void inventoryTick(ItemStack st, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) Perishable.inventoryTick(st, level.getGameTime());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String name = LabService.patientName(stack);
        tooltip.add(Component.translatable("rpmedicine.tooltip.sample_of", name != null ? name : "?").withStyle(ChatFormatting.GRAY));
        if (level != null && spoiled(stack, level.getGameTime())) {
            tooltip.add(Component.translatable("rpmedicine.tooltip.sample_spoiled").withStyle(ChatFormatting.DARK_RED));
        } else if (level != null && Perishable.started(stack)) {
            long now = level.getGameTime();
            tooltip.add(Perishable.shelfLine(Perishable.cold(stack), Perishable.warmTicks(stack, now), Perishable.ageTicks(stack, now),
                    faygolover.rpmedicine.core.MedicalSettings.get().sampleSpoilWarmHours, Perishable.NO_LIMIT));
        }
        tooltip.add(Component.translatable("item.rpmedicine.blood_sample.desc").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 3D-модель пробирки (client/geo) — в руке и в инвентаре. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(faygolover.rpmedicine.client.ItemPoses.EXTENSIONS);
    }
}
