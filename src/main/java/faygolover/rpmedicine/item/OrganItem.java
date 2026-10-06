package faygolover.rpmedicine.item;

import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.Organ;
import faygolover.rpmedicine.core.Treatments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Орган в контейнере (ТЗ третьего этапа, п. 7): какой орган, его повреждение, группа крови донора, когда
 * изъят. Вне холодильника портится за час, в холодильнике — за трое суток; испорченный — повреждение 100.
 */
public class OrganItem extends MedicalItem implements Perishable.Item {
    public OrganItem(Properties props) {
        super(props);
    }

    public static ItemStack create(net.minecraft.world.item.Item item, Organ organ, double damage, @Nullable BloodType donor, String donorName, long now) {
        ItemStack st = new ItemStack(item);
        var t = st.getOrCreateTag();
        t.putString("Organ", organ.id);
        t.putFloat("Damage", (float) damage);
        if (donor != null) t.putString("Blood", donor.id);
        t.putString("Donor", donorName);
        Perishable.start(st, now);
        return st;
    }

    @Nullable
    public static Organ organ(ItemStack st) {
        return st.hasTag() ? Organ.byId(st.getTag().getString("Organ")).orElse(null) : null;
    }

    public static boolean spoiled(ItemStack st, long now) {
        MedicalSettings s = MedicalSettings.get();
        // В холодильнике порча стоит совсем (решения, п. 1.16).
        return Perishable.spoiled(st, now, s.organSpoilWarmHours, Perishable.NO_LIMIT);
    }

    /** Содержимое для пересадки. */
    @Nullable
    public static Treatments.DonorOrgan donorOrgan(ItemStack st, long now) {
        Organ o = organ(st);
        if (o == null) return null;
        var t = st.getTag();
        BloodType b = t.contains("Blood") ? BloodType.byId(t.getString("Blood")).orElse(null) : null;
        return new Treatments.DonorOrgan(o, t.getFloat("Damage"), b, spoiled(st, now));
    }

    @Override
    public Component getName(ItemStack st) {
        Organ o = organ(st);
        return o == null ? super.getName(st) : Component.translatable("item.rpmedicine.organ.of", Component.translatable(o.translationKey()));
    }

    @Override
    public void inventoryTick(ItemStack st, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) Perishable.inventoryTick(st, level.getGameTime());
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(st, level, tooltip, flag);
        if (!st.hasTag()) return;
        var t = st.getTag();
        tooltip.add(Component.translatable("rpmedicine.tooltip.organ_donor", t.getString("Donor"),
                t.contains("Blood") ? BloodType.byId(t.getString("Blood")).map(b -> b.label).orElse("?") : "?").withStyle(ChatFormatting.GRAY));
        if (level != null && spoiled(st, level.getGameTime())) {
            tooltip.add(Component.translatable("rpmedicine.tooltip.organ_spoiled").withStyle(ChatFormatting.DARK_RED));
        } else if (level != null && Perishable.started(st)) {
            var s = faygolover.rpmedicine.core.MedicalSettings.get();
            long now = level.getGameTime();
            tooltip.add(Perishable.shelfLine(Perishable.cold(st), Perishable.warmTicks(st, now), Perishable.ageTicks(st, now),
                    s.organSpoilWarmHours, Perishable.NO_LIMIT));
        }
    }
}
