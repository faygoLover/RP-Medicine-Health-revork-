package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;

/**
 * Органы как отдельные шкалы повреждения 0–100 (ТЗ третьего этапа, п. 2). Мозг — отдельная шкала
 * первого этапа. Порядок (ordinal) используется в сохранении — не менять.
 */
public enum Organ {
    HEART("heart", BodyPart.CHEST),
    LUNGS("lungs", BodyPart.CHEST),
    LIVER("liver", BodyPart.ABDOMEN),
    KIDNEYS("kidneys", BodyPart.ABDOMEN),
    INTESTINES("intestines", BodyPart.ABDOMEN);

    public static final Organ[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public final String id;
    /** Часть тела, в которой лежит орган. */
    public final BodyPart part;

    Organ(String id, BodyPart part) {
        this.id = id;
        this.part = part;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public String translationKey() {
        return "rpmedicine.organ." + id;
    }

    public static Optional<Organ> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (Organ o : VALUES) if (o.id.equals(s)) return Optional.of(o);
        return Optional.empty();
    }

    public static Organ byOrdinal(int i) {
        return i >= 0 && i < COUNT ? VALUES[i] : HEART;
    }
}
