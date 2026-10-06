package faygolover.rpmedicine.item;

import faygolover.rpmedicine.core.BloodType;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.Treatments;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Пакет крови (ТЗ второго этапа, п. 4.2). В NBT — группа донора, когда взята кровь и сколько пакет
 * пролежал вне холодильника. Время — игровое время мира ({@code getGameTime}): идёт, только пока
 * работает сервер, и не зависит от смены дня и ночи.
 * <p>
 * Порча считается лениво: «тёплое» время = накопленное + (сейчас − последняя отметка), если пакет
 * не в холодильнике. Холодильник отмечается при закрытии его меню ({@code BloodService}); первый
 * тик в инвентаре после холодильника снимает отметку.
 */
public class BloodBagItem extends MedicalItem {
    private static final String TYPE = "BloodType";
    private static final String COLLECTED = "Collected";
    private static final String WARM = "Warm";
    private static final String SEEN = "Seen";
    private static final String COLD = "Cold";
    private static final String DONOR = "Donor";

    public BloodBagItem(Properties props) {
        super(props);
    }

    /** Заполнить пакет кровью донора. */
    public static void fill(ItemStack stack, @Nullable BloodType type, long now, String donor) {
        CompoundTag t = stack.getOrCreateTag();
        if (type != null) t.putString(TYPE, type.id);
        else t.remove(TYPE);
        t.putLong(COLLECTED, now);
        t.putLong(WARM, 0);
        t.putLong(SEEN, now);
        t.putBoolean(COLD, false);
        t.putString(DONOR, donor);
    }

    @Nullable
    public static BloodType type(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(TYPE) ? BloodType.byId(t.getString(TYPE)).orElse(null) : null;
    }

    public static String donor(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null ? t.getString(DONOR) : "";
    }

    /** Сколько тиков пакет пролежал вне холодильника к моменту {@code now}. */
    public static long warmTicks(ItemStack stack, long now) {
        CompoundTag t = stack.getTag();
        if (t == null) return 0;
        long warm = t.getLong(WARM);
        if (!t.getBoolean(COLD)) warm += Math.max(0, now - t.getLong(SEEN));
        return warm;
    }

    public static boolean isSpoiled(ItemStack stack, long now) {
        CompoundTag t = stack.getTag();
        if (t == null || !t.contains(COLLECTED)) return false;
        MedicalSettings s = MedicalSettings.get();
        // В холодильнике порча стоит совсем (решения, п. 1.16): считается только время в тепле.
        return warmTicks(stack, now) > s.bloodSpoilWarmHours * 72000;
    }

    /** Положили в холодильник (или вынули в обычный контейнер): зафиксировать тёплое время. */
    public static void setCold(ItemStack stack, boolean cold, long now) {
        CompoundTag t = stack.getTag();
        if (t == null || !t.contains(COLLECTED)) return;
        if (t.getBoolean(COLD) == cold) return;
        t.putLong(WARM, warmTicks(stack, now));
        t.putLong(SEEN, now);
        t.putBoolean(COLD, cold);
    }

    /** Содержимое для переливания. */
    public static Treatments.Bag bag(ItemStack stack, long now) {
        return new Treatments.Bag(type(stack), MedicalSettings.get().bloodBagVolume, isSpoiled(stack, now));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // Вынули из холодильника в инвентарь — с этого момента пакет снова «тёплый».
        if (!level.isClientSide && stack.hasTag() && stack.getTag().getBoolean(COLD)) setCold(stack, false, level.getGameTime());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!stack.hasTag() || !stack.getTag().contains(COLLECTED)) return;
        BloodType type = type(stack);
        tooltip.add(Component.translatable("rpmedicine.tooltip.blood_type", type != null ? type.label : "?").withStyle(ChatFormatting.RED));
        if (level != null && isSpoiled(stack, level.getGameTime())) {
            tooltip.add(Component.translatable("rpmedicine.tooltip.blood_spoiled").withStyle(ChatFormatting.DARK_RED));
        } else if (level != null) {
            // Срок хранения и холод (замечание 37).
            MedicalSettings s = MedicalSettings.get();
            long now = level.getGameTime();
            tooltip.add(Perishable.shelfLine(stack.getTag().getBoolean(COLD), warmTicks(stack, now), now - stack.getTag().getLong(COLLECTED),
                    s.bloodSpoilWarmHours, s.bloodSpoilFridgeDays * 24));
        }
    }
}
