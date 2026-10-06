package faygolover.rpmedicine.item;

import faygolover.rpmedicine.core.Drug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Шприц, набранный из флакона (решения, п. 1.16): препарат и объём в мл (1 мл = 1 стандартная доза). */
public class FilledSyringeItem extends MedicalItem {
    private static final String DRUG = "Drug";
    private static final String ML = "Ml";

    public FilledSyringeItem(Properties props) {
        super(props);
    }

    public static ItemStack of(String drug, double ml) {
        ItemStack st = new ItemStack(faygolover.rpmedicine.registry.ModItems.FILLED_SYRINGE.get());
        CompoundTag t = st.getOrCreateTag();
        t.putString(DRUG, drug);
        t.putFloat(ML, (float) ml);
        return st;
    }

    @Nullable
    public static String drug(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.contains(DRUG) ? t.getString(DRUG) : null;
    }

    public static double ml(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null ? t.getFloat(ML) : 0;
    }

    @Nullable
    public static Drug drugOf(ItemStack st) {
        String id = drug(st);
        return id == null ? null : faygolover.rpmedicine.data.DrugRules.byId(id);
    }

    /** Предмет флакона этого препарата — для названия. */
    public static Component drugName(String id) {
        int c = id.indexOf(':');
        return Component.translatable("item." + (c < 0 ? "rpmedicine" : id.substring(0, c)) + "." + (c < 0 ? id : id.substring(c + 1)));
    }

    @Override
    public Component getName(ItemStack st) {
        String id = drug(st);
        if (id == null) return super.getName(st);
        return Component.translatable("item.rpmedicine.filled_syringe.named", drugName(id),
                String.format(Locale.ROOT, "%.1f", ml(st)).replace('.', ','));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rpmedicine.filled_syringe.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
