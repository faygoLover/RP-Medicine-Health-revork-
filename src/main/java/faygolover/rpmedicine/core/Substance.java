package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;

/**
 * Вещества с толерантностью, зависимостью и ломкой (ТЗ третьего этапа, п. 9). Порядок — в сохранении.
 * Числа по умолчанию: прирост толерантности за дозу, база шанса зависимости, через сколько часов
 * без вещества ломка.
 */
public enum Substance {
    OPIOID("opioid", 12, 0.04, 8),
    BENZO("benzo", 8, 0.03, 12),
    ALCOHOL("alcohol", 5, 0.01, 12),
    NICOTINE("nicotine", 6, 0.05, 4),
    CAFFEINE("caffeine", 5, 0.02, 6),
    STIMULANT("stimulant", 15, 0.06, 6);

    public static final Substance[] VALUES = values();

    public final String id;
    public final double toleranceGain;
    public final double dependenceBase;
    public final double withdrawalHours;

    Substance(String id, double toleranceGain, double dependenceBase, double withdrawalHours) {
        this.id = id;
        this.toleranceGain = toleranceGain;
        this.dependenceBase = dependenceBase;
        this.withdrawalHours = withdrawalHours;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public String translationKey() {
        return "rpmedicine.substance." + id;
    }

    public static Optional<Substance> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (Substance v : VALUES) if (v.id.equals(s)) return Optional.of(v);
        return Optional.empty();
    }
}
