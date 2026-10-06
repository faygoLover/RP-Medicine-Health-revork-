package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.Drug;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.data.ItemRules;
import faygolover.rpmedicine.item.FilledSyringeItem;
import faygolover.rpmedicine.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Набор шприцем из флакона (решения, п. 1.16): шприц в руке, флакон во второй — мини-игра (проткнуть пробку,
 * набрать мл). Во флаконе убывают мл, шприц становится набранным. Неопытный объём выбирать не умеет —
 * набирает 0,75–1,25 мл случайно.
 */
public final class VialService {
    private VialService() {}

    private static final Map<UUID, Long> PENDING = new HashMap<>();

    /** Шприц и флакон в руках — да. */
    public static boolean canDraw(ServerPlayer sp) {
        return sp.getMainHandItem().is(ModItems.SYRINGE.get()) && ModItems.isVial(sp.getOffhandItem());
    }

    public static void start(ServerPlayer sp) {
        ItemStack vial = sp.getOffhandItem();
        Drug d = ItemRules.drugFor(vial);
        if (d == null) return;
        MedicalSettings s = MedicalSettings.get();
        int level = Medical.medicineLevel(sp);
        boolean skilled = level >= s.dosingMinLevel;
        double left = ModItems.remainingDoses(vial);
        PENDING.put(sp.getUUID(), sp.level().getGameTime());
        float hint = skilled ? (float) Math.min(left, hint(d, s)) : 0;
        faygolover.rpmedicine.network.Network.send(sp, new faygolover.rpmedicine.network.DrawPacket.Request(vial.getHoverName(), label(d), (float) left, hint, skilled, level));
    }

    /** Сколько набрать: стандартная доза; для капельницы — чтобы держать уровень на время пакета. */
    static double hint(Drug d, MedicalSettings s) {
        if (d.form() == Drug.Form.DRIP) {
            double ml = Math.log(2) / d.halfLife() * s.ivAdditiveDripSeconds;
            return Math.max(0.5, Math.round(ml * 10) / 10.0);
        }
        return 1.0;
    }

    /** Надпись на флаконе: концентрация (видна всем — это надпись). */
    public static Component label(Drug d) {
        double mg = d.kinetics().mgPerDose();
        if (mg <= 0) return Component.empty();
        String num = mg == Math.rint(mg) ? String.valueOf((long) mg) : String.format(Locale.ROOT, "%.1f", mg).replace('.', ',');
        return Component.translatable("rpmedicine.vial.label", num, Component.translatable("rpmedicine.unit." + d.kinetics().unit()));
    }

    public static void onChoice(ServerPlayer sp, float ml, int misses) {
        Long t = PENDING.remove(sp.getUUID());
        if (t == null || ml <= 0 || sp.level().getGameTime() - t > 20 * 120) return;
        if (!canDraw(sp)) return;
        ItemStack vial = sp.getOffhandItem();
        Drug d = ItemRules.drugFor(vial);
        if (d == null) return;
        double left = ModItems.remainingDoses(vial);
        double amount = Medical.medicineLevel(sp) >= MedicalSettings.get().dosingMinLevel
                ? ml : 0.75 + sp.getRandom().nextDouble() * 0.5;
        amount = Math.max(0.1, Math.min(left, Math.round(amount * 10) / 10.0));
        if (!sp.getAbilities().instabuild) {
            int units = (int) Math.round(amount * 10);
            vial.hurtAndBreak(units, sp, p -> {});
        }
        ItemStack syringe = sp.getMainHandItem();
        syringe.shrink(1);
        ItemStack filled = FilledSyringeItem.of(d.id(), amount);
        if (syringe.isEmpty()) sp.setItemInHand(InteractionHand.MAIN_HAND, filled);
        else if (!sp.getInventory().add(filled)) sp.drop(filled, false);
        sp.displayClientMessage(Component.translatable("rpmedicine.vial.drawn",
                String.format(Locale.ROOT, "%.1f", amount).replace('.', ','), vial.isEmpty() ? Component.empty() : vial.getHoverName())
                .withStyle(ChatFormatting.WHITE), true);
    }
}
