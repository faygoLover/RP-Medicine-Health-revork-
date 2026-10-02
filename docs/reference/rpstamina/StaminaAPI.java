package faygolover.rpstamina.api;

import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.capability.StaminaProvider;
import faygolover.rpstamina.core.Source;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Публичный API для других модов (например, rpperks). Только серверная сторона.
 * Подключайте мягко: проверяйте ModList.get().isLoaded("rpstamina").
 */
public final class StaminaAPI {
    private StaminaAPI() {}

    /** Текущая выносливость (если механика не действует на игрока — максимум). */
    public static float get(ServerPlayer player) {
        PlayerStamina s = StaminaProvider.get(player);
        if (s == null || s.stamina < 0f) return StaminaManager.max(player);
        return s.stamina;
    }

    public static float getMax(ServerPlayer player) {
        return StaminaManager.max(player);
    }

    public static boolean isExhausted(ServerPlayer player) {
        PlayerStamina s = StaminaProvider.get(player);
        return s != null && s.exhausted;
    }

    /** Трата через общий конвейер: множитель cost_multiplier, событие StaminaConsumeEvent, состояние измотанности. */
    public static void consume(ServerPlayer player, float amount, ResourceLocation source) {
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.consume(player, s, source, amount, Source.Category.OTHER, false, false);
    }

    /** Прямое добавление (без множителей и без расхода голода). Отрицательное значение уменьшает без задержки. */
    public static void add(ServerPlayer player, float amount) {
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.addDirect(player, s, amount);
    }

    public static void set(ServerPlayer player, float value) {
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.setDirect(player, s, value);
    }
}
