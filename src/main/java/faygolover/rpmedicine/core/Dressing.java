package faygolover.rpmedicine.core;

/** Что наложено на рану. Порядок (ordinal) используется в сохранении — не менять. */
public enum Dressing {
    NONE("none"),
    BANDAGE("bandage"),
    PRESSURE("pressure"),
    HEMOSTATIC("hemostatic");

    public static final Dressing[] VALUES = values();

    public final String id;

    Dressing(String id) {
        this.id = id;
    }

    /** Множитель кровотечения раны под этой повязкой в зависимости от силы кровотечения. */
    public double bleedFactor(double bleed, MedicalSettings s) {
        return switch (this) {
            case NONE -> 1.0;
            case BANDAGE -> bleed <= s.slightBleedMax ? 0.0 : bleed <= s.moderateBleedMax ? 0.15 : 0.6;
            case PRESSURE -> bleed <= s.moderateBleedMax ? 0.0 : bleed <= s.heavyBleedMax ? 0.1 : 0.4;
            case HEMOSTATIC -> 0.0;
        };
    }

    public static Dressing byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
    }
}
