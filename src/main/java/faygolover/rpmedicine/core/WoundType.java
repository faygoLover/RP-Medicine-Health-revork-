package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;

/** Типы ран (п. 3.1 ТЗ). Порядок (ordinal) используется в сохранении и сети — не менять. */
public enum WoundType {
    BRUISE("bruise"),
    CUT("cut"),
    STAB("stab"),
    GUNSHOT("gunshot"),
    SHRAPNEL("shrapnel"),
    BURN("burn"),
    BITE("bite");

    public static final WoundType[] VALUES = values();

    public final String id;

    WoundType(String id) {
        this.id = id;
    }

    /** Проникающая рана: в груди даёт пневмоторакс, в груди и животе — внутреннее кровотечение. */
    public boolean isPenetrating() {
        return this == STAB || this == GUNSHOT || this == SHRAPNEL;
    }

    public static Optional<WoundType> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (WoundType t : VALUES) if (t.id.equals(s)) return Optional.of(t);
        return Optional.empty();
    }

    public static WoundType byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : BRUISE;
    }

    public String translationKey() {
        return "rpmedicine.wound." + id;
    }
}
