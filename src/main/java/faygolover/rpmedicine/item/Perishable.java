package faygolover.rpmedicine.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Портящееся содержимое предмета (орган, конечность; ТЗ третьего этапа, п. 7.2): считается время вне
 * холодильника и общий возраст. Как у пакета крови: холодильник «замораживает» тёплое время.
 */
public final class Perishable {
    private Perishable() {}

    private static final String TAKEN = "Taken";
    private static final String WARM = "Warm";
    private static final String SEEN = "Seen";
    private static final String COLD = "Cold";

    /** Предмет с портящимся содержимым: холодильник его охлаждает (см. {@code BloodService.onContainerClose}). */
    public interface Item {}

    public static void start(ItemStack st, long now) {
        CompoundTag t = st.getOrCreateTag();
        t.putLong(TAKEN, now);
        t.putLong(WARM, 0);
        t.putLong(SEEN, now);
        t.putBoolean(COLD, false);
    }

    public static boolean started(ItemStack st) {
        return st.hasTag() && st.getTag().contains(TAKEN);
    }

    /** Тиков вне холодильника к моменту {@code now}. */
    public static long warmTicks(ItemStack st, long now) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains(TAKEN)) return 0;
        long warm = t.getLong(WARM);
        if (!t.getBoolean(COLD)) warm += Math.max(0, now - t.getLong(SEEN));
        return warm;
    }

    public static long ageTicks(ItemStack st, long now) {
        CompoundTag t = st.getTag();
        return t == null || !t.contains(TAKEN) ? 0 : Math.max(0, now - t.getLong(TAKEN));
    }

    /** Испорчено: тепло дольше {@code warmHours} или всего дольше {@code maxHours}. */
    public static boolean spoiled(ItemStack st, long now, double warmHours, double maxHours) {
        if (!started(st)) return false;
        return warmTicks(st, now) > warmHours * 72000 || ageTicks(st, now) > maxHours * 72000;
    }

    public static void setCold(ItemStack st, boolean cold, long now) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains(TAKEN) || t.getBoolean(COLD) == cold) return;
        t.putLong(WARM, warmTicks(st, now));
        t.putLong(SEEN, now);
        t.putBoolean(COLD, cold);
    }

    /** В инвентаре — снова тепло. */
    public static void inventoryTick(ItemStack st, long now) {
        if (st.hasTag() && st.getTag().getBoolean(COLD)) setCold(st, false, now);
    }
}
