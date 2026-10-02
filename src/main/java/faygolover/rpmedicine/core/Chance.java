package faygolover.rpmedicine.core;

/**
 * Шанс осложнения, растущий с тяжестью раны: {@code (тяжесть − min) × perSeverity}, не больше max.
 * Значения — из датапака {@code damage_sources}.
 */
public final class Chance {
    public static final Chance NEVER = new Chance(0, 0, 0);

    public final double minSeverity;
    public final double perSeverity;
    public final double max;

    public Chance(double minSeverity, double perSeverity, double max) {
        this.minSeverity = minSeverity;
        this.perSeverity = perSeverity;
        this.max = max;
    }

    public double at(double severity) {
        if (perSeverity <= 0 || max <= 0) return 0;
        return Math.max(0, Math.min(max, (severity - minSeverity) * perSeverity));
    }

    public boolean isNever() {
        return perSeverity <= 0 || max <= 0;
    }
}
