package faygolover.rpperks.perk;

import faygolover.rpperks.Config;
import faygolover.rpperks.capability.PlayerPerks;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Правила самостоятельного выбора перков игроком.
 * Используются и сервером (проверка действия), и клиентом (подсказки и неактивные кнопки в окне),
 * поэтому игрок видит ту же причину отказа, которую потом проверит сервер.
 * Каждый метод возвращает причину, почему действие недоступно, или null, если можно.
 */
public final class PerkRules {
    private PerkRules() {}

    /** Может ли игрок видеть перк в окне. */
    public static boolean isVisible(Perk perk, boolean canSeeHidden) {
        return canSeeHidden || (!Config.isHidden(perk) && perk.getCategory() != Perk.Category.SERVICE);
    }

    @Nullable
    public static Component whyCannotTake(PlayerPerks data, Perk perk, boolean canSeeHidden) {
        if (!Config.get(Config.PLAYERS_CAN_SELECT)) return reason("selection_disabled");
        if (data.isSelectionLocked()) return reason("selection_locked");
        if (data.owns(perk)) return reason("owned");
        if (perk.getCategory() == Perk.Category.SERVICE || (Config.isHidden(perk) && !canSeeHidden)) {
            return reason("admin_only");
        }
        if (data.isBlocked(perk)) return reason("blocked");
        for (Perk conflict : Config.getConflicts(perk)) {
            if (data.owns(conflict)) {
                return Component.translatable("gui.rpperks.reason.conflict", Component.translatable(conflict.getNameKey()));
            }
        }
        if (perk.isPositive() && !Config.get(Config.ALLOW_NEGATIVE_BALANCE) && data.getPoints() - perk.getCost() < 0) {
            return Component.translatable("gui.rpperks.reason.no_points", perk.getCost());
        }
        return null;
    }

    @Nullable
    public static Component whyCannotRefuse(PlayerPerks data, Perk perk) {
        if (!data.owns(perk)) return reason("not_owned");
        if (perk.getCategory() == Perk.Category.SERVICE) return reason("admin_only");
        if (data.getRefuseTokens() <= 0) return reason("no_refuse_token");
        int delta = data.getDelta(perk);
        // Отказ от негативного перка забирает баллы, которые он дал
        if (delta > 0 && !Config.get(Config.ALLOW_NEGATIVE_BALANCE) && data.getPoints() - delta < 0) {
            return Component.translatable("gui.rpperks.reason.no_points_refund", delta);
        }
        return null;
    }

    @Nullable
    public static Component whyCannotReset(PlayerPerks data) {
        if (data.getResettablePerks().isEmpty()) return reason("nothing_to_reset");
        if (data.getResetTokens() <= 0) return reason("no_reset_token");
        return null;
    }

    private static Component reason(String key) {
        return Component.translatable("gui.rpperks.reason." + key);
    }
}
