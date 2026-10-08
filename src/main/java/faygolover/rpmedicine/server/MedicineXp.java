package faygolover.rpmedicine.server;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpmedicine.core.TreatmentAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Опыт «Медицины» за лечение (рост навыков ведёт RP Perks через RP Core; без него — ничего). Правила автора
 * (итоговый дизайн §3.3): сложнее действие — больше опыта; простое для своего уровня даёт всё меньше, потом
 * ничего; ненужное («продавленное») — ничего; повторы на одном пациенте подряд убывают; ошибка — половина.
 */
public final class MedicineXp {
    private MedicineXp() {}

    /** Повторы: медик → пациент → {сколько раз, когда последний (тик)}. Через 10 минут счёт обнуляется. */
    private static final Map<UUID, Map<UUID, long[]>> REPEATS = new HashMap<>();
    private static final long REPEAT_RESET_TICKS = 20 * 60 * 10;

    public static void award(ServerPlayer actor, LivingEntity target, TreatmentAction action, int minLevel, int level, boolean error) {
        if (actor == target && action.isDiagnostic()) return;
        double base = 2.0 + minLevel;
        if (action.isDiagnostic()) base *= 0.5;
        double relevance = Math.max(0, Math.min(1, 1.0 - (level - minLevel) / 4.0));
        if (relevance <= 0) return;
        long now = actor.level().getGameTime();
        long[] rep = REPEATS.computeIfAbsent(actor.getUUID(), k -> new HashMap<>())
                .computeIfAbsent(target.getUUID(), k -> new long[]{0, now});
        if (now - rep[1] > REPEAT_RESET_TICKS) rep[0] = 0;
        double repeat = Math.pow(0.6, rep[0]);
        rep[0]++;
        rep[1] = now;
        double xp = base * relevance * repeat * (error ? 0.5 : 1.0);
        RpCoreAPI.practice(actor, RpIds.MEDICINE, xp);
    }

    public static void forget(ServerPlayer p) {
        REPEATS.remove(p.getUUID());
    }
}
