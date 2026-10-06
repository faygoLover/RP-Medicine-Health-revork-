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

    /**
     * Строка подсказки о сроке: «в холоде, ещё ~N» или «осталось ~N в тепле» (замечание 37).
     * Время — реальное (тики сервера), как и порча.
     */
    /** Без общего срока: в холоде порча стоит совсем (решения, п. 1.16). */
    public static final double NO_LIMIT = 1e9;

    public static net.minecraft.network.chat.Component shelfLine(boolean cold, long warmTicks, long ageTicks, double warmHours, double maxHours) {
        if (cold && maxHours >= NO_LIMIT)
            return net.minecraft.network.chat.Component.translatable("rpmedicine.tooltip.shelf_frozen").withStyle(net.minecraft.ChatFormatting.AQUA);
        long warmLeft = Math.max(0, (long) (warmHours * 72000) - warmTicks);
        long totalLeft = Math.max(0, (long) (maxHours * 72000) - ageTicks);
        long left = cold ? totalLeft : Math.min(warmLeft, totalLeft);
        String time = duration(left);
        return cold
                ? net.minecraft.network.chat.Component.translatable("rpmedicine.tooltip.shelf_cold", time).withStyle(net.minecraft.ChatFormatting.AQUA)
                : net.minecraft.network.chat.Component.translatable("rpmedicine.tooltip.shelf_warm", time,
                duration(warmLeft)).withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    /** Тики → «2 ч 15 мин» / «40 мин» / «3 дн». */
    public static String duration(long ticks) {
        long min = ticks / 1200;
        if (min >= 48 * 60) return (min / (24 * 60)) + " " + net.minecraft.network.chat.Component.translatable("rpmedicine.unit.days").getString();
        if (min >= 60) return (min / 60) + " " + net.minecraft.network.chat.Component.translatable("rpmedicine.unit.hours").getString()
                + (min % 60 > 0 ? " " + (min % 60) + " " + net.minecraft.network.chat.Component.translatable("rpmedicine.unit.minutes").getString() : "");
        return Math.max(1, min) + " " + net.minecraft.network.chat.Component.translatable("rpmedicine.unit.minutes").getString();
    }

    public static boolean cold(ItemStack st) {
        return st.hasTag() && st.getTag().getBoolean(COLD);
    }

    /** В инвентаре — снова тепло. */
    public static void inventoryTick(ItemStack st, long now) {
        // Взяли из вкладки творческого — срок пошёл.
        if (st.hasTag() && !started(st)) start(st, now);
        if (st.hasTag() && st.getTag().getBoolean(COLD)) setCold(st, false, now);
    }
}
