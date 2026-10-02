package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;

/** Девять частей тела. Порядок (ordinal) используется в сохранении и сети — не менять. */
public enum BodyPart {
    HEAD("head", Kind.HEAD, Side.NONE),
    CHEST("chest", Kind.TORSO, Side.NONE),
    ABDOMEN("abdomen", Kind.TORSO, Side.NONE),
    RIGHT_ARM("right_arm", Kind.ARM, Side.RIGHT),
    LEFT_ARM("left_arm", Kind.ARM, Side.LEFT),
    RIGHT_LEG("right_leg", Kind.LEG, Side.RIGHT),
    LEFT_LEG("left_leg", Kind.LEG, Side.LEFT),
    RIGHT_FOOT("right_foot", Kind.FOOT, Side.RIGHT),
    LEFT_FOOT("left_foot", Kind.FOOT, Side.LEFT);

    public enum Kind { HEAD, TORSO, ARM, LEG, FOOT }

    public enum Side { NONE, RIGHT, LEFT }

    public static final BodyPart[] VALUES = values();

    public final String id;
    public final Kind kind;
    public final Side side;

    BodyPart(String id, Kind kind, Side side) {
        this.id = id;
        this.kind = kind;
        this.side = side;
    }

    public boolean isArm() { return kind == Kind.ARM; }

    /** Нога или стопа: от них зависит ходьба. */
    public boolean isLowerLimb() { return kind == Kind.LEG || kind == Kind.FOOT; }

    /** Конечность, на которую можно наложить жгут (рука или нога). */
    public boolean acceptsTourniquet() { return kind == Kind.ARM || kind == Kind.LEG; }

    /** Конечность, которую можно сломать и зашинировать. */
    public boolean isLimb() { return kind == Kind.ARM || kind == Kind.LEG || kind == Kind.FOOT; }

    public boolean isTorso() { return kind == Kind.TORSO; }

    /** Часть, кровоток которой перекрывает жгут на указанной части (нога перекрывает и стопу той же стороны). */
    public boolean isDistalTo(BodyPart tourniquetPart) {
        if (this == tourniquetPart) return true;
        return tourniquetPart.kind == Kind.LEG && kind == Kind.FOOT && side == tourniquetPart.side;
    }

    /** Стопа той же стороны для ноги и наоборот. */
    public BodyPart pairedLowerLimb() {
        return switch (this) {
            case RIGHT_LEG -> RIGHT_FOOT;
            case LEFT_LEG -> LEFT_FOOT;
            case RIGHT_FOOT -> RIGHT_LEG;
            case LEFT_FOOT -> LEFT_LEG;
            default -> this;
        };
    }

    public BodyPart mirror() {
        return switch (this) {
            case RIGHT_ARM -> LEFT_ARM;
            case LEFT_ARM -> RIGHT_ARM;
            case RIGHT_LEG -> LEFT_LEG;
            case LEFT_LEG -> RIGHT_LEG;
            case RIGHT_FOOT -> LEFT_FOOT;
            case LEFT_FOOT -> RIGHT_FOOT;
            default -> this;
        };
    }

    public static Optional<BodyPart> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (BodyPart p : VALUES) if (p.id.equals(s)) return Optional.of(p);
        return Optional.empty();
    }

    public static BodyPart byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : CHEST;
    }

    public String translationKey() {
        return "rpmedicine.part." + id;
    }
}
