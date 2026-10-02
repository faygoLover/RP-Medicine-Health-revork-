package faygolover.rpmedicine.integration.lso;

import net.minecraft.server.level.ServerPlayer;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.TemperatureCapability;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.TemperatureProvider;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstCapability;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstProvider;

import java.util.UUID;

/**
 * Legendary Survival Overhaul (необязательный): жажда и температура берутся из него, а лихорадка
 * поднимает его температуру (ТЗ второго этапа, п. 12). Класс загружается, только если LSO установлен.
 */
public final class LsoCompat {
    private LsoCompat() {}

    /** Модификатор температуры LSO от лихорадки. */
    private static final UUID FEVER_ID = UUID.fromString("6d1f0a52-5b4e-4b8e-9a51-1f6c2e0d7a10");
    /** Норма температуры LSO. */
    private static final float NORMAL_LOW = 16f;
    private static final float NORMAL_HIGH = 24f;

    /** Вода 0–1 или −1, если у игрока нет жажды LSO. */
    public static double hydration(ServerPlayer sp) {
        ThirstCapability c = sp.getCapability(ThirstProvider.THIRST_CAPABILITY).resolve().orElse(null);
        return c == null ? -1 : Math.max(0, Math.min(1, c.getHydrationLevel() / 20.0));
    }

    /** Температура LSO за пределами нормы в единицах LSO (минус — холод), 0 — в норме или нет данных. */
    public static double temperatureOffset(ServerPlayer sp) {
        TemperatureCapability c = sp.getCapability(TemperatureProvider.TEMPERATURE_CAPABILITY).resolve().orElse(null);
        if (c == null) return 0;
        float t = c.getTemperatureLevel();
        if (t < NORMAL_LOW) return t - NORMAL_LOW;
        if (t > NORMAL_HIGH) return t - NORMAL_HIGH;
        return 0;
    }

    /** Лихорадка поднимает температуру LSO (модификатор пересчитывается каждый шаг; 0 — снять). */
    public static void setFever(ServerPlayer sp, double lsoUnits) {
        TemperatureUtil.addTemperatureModifier(sp, lsoUnits, FEVER_ID);
    }

    /** Команда food add: снять воду LSO. */
    public static void loseThirst(ServerPlayer sp, double points) {
        ThirstCapability c = sp.getCapability(ThirstProvider.THIRST_CAPABILITY).resolve().orElse(null);
        if (c == null) return;
        c.setHydrationLevel(Math.max(0, c.getHydrationLevel() - (int) Math.round(points)));
    }
}
