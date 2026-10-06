package faygolover.rpmedicine.item;

import faygolover.rpmedicine.network.LabResultPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Бланк анализа крови (замечание 06.10): выдаётся после анализа, ПКМ — перечитать. В медкарту попадает,
 * только если его туда вложить (кнопка в медкарте пациента).
 */
public class LabReportItem extends Item {
    public LabReportItem(Properties props) {
        super(props);
    }

    public static ItemStack of(LabResultPacket r, @Nullable UUID patient, long date) {
        ItemStack st = new ItemStack(faygolover.rpmedicine.registry.ModItems.LAB_REPORT.get());
        CompoundTag t = st.getOrCreateTag();
        t.putString("Patient", r.patient());
        if (patient != null) t.putUUID("PatientId", patient);
        t.putString("Type", r.bloodType());
        ListTag v = new ListTag();
        for (double d : r.values()) v.add(net.minecraft.nbt.FloatTag.valueOf((float) d));
        t.put("Values", v);
        t.putBoolean("Sepsis", r.sepsis());
        t.putByte("Compat", r.compat());
        t.putString("Bag", r.bag());
        t.putLong("Date", date);
        return st;
    }

    @Nullable
    public static LabResultPacket read(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains("Values")) return null;
        ListTag v = t.getList("Values", Tag.TAG_FLOAT);
        double[] values = new double[LabResultPacket.ROWS.length];
        for (int i = 0; i < values.length && i < v.size(); i++) values[i] = v.getFloat(i);
        return new LabResultPacket(t.getString("Patient"), t.getString("Type"), values, t.getBoolean("Sepsis"), t.getByte("Compat"), t.getString("Bag"));
    }

    @Nullable
    public static UUID patient(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.hasUUID("PatientId") ? t.getUUID("PatientId") : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        LabResultPacket r = read(st);
        if (r != null && level.isClientSide)
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> faygolover.rpmedicine.client.ClientHandlers.openLab(r));
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = st.getTag();
        if (t != null && t.contains("Patient"))
            tooltip.add(Component.translatable("rpmedicine.tooltip.sample_of", t.getString("Patient")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rpmedicine.lab_report.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
