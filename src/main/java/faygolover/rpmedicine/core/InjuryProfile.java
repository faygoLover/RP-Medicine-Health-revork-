package faygolover.rpmedicine.core;

/**
 * Как источник урона превращается в травмы: тип раны, множитель тяжести, шансы осложнений,
 * как выбирать часть тела. Читается из датапака {@code damage_sources}.
 */
public final class InjuryProfile {
    /** Как выбрать часть тела. */
    public enum Location {
        /** По точке удара (высота и сторона). */
        HIT_POINT,
        /** Падение: стопы и ноги, с большой высоты — живот и голова. */
        FALL,
        /** Взрыв: несколько частей, обращённых к взрыву. */
        EXPLOSION,
        /** Огонь: одна-две случайные части. */
        FIRE,
        /** Лава: сразу несколько частей. */
        LAVA,
        /** Случайная часть. */
        RANDOM,
        HEAD,
        LEGS,
        CHEST,
        /** Ран нет, действует прямо на физиологию. */
        NONE
    }

    public String id = "default";
    /** null — ран нет (только физиология). */
    public WoundType wound = WoundType.BRUISE;
    public Location location = Location.HIT_POINT;
    public double severityMultiplier = 1.0;

    public Chance fracture = Chance.NEVER;
    /** Доля открытых среди переломов. */
    public double openFractureFraction = 0.0;
    public Chance arterial = Chance.NEVER;
    public Chance internal = Chance.NEVER;
    public Chance foreignBody = Chance.NEVER;
    public int foreignBodyMin = 1;
    public int foreignBodyMax = 1;
    public Chance concussion = Chance.NEVER;
    /** Контузия, единиц на единицу тяжести. */
    public double concussionPerSeverity = 2.0;
    public Chance pneumothorax = Chance.NEVER;

    // Физиология без ран (утопление, голод, магия): на единицу урона.
    public double spo2PerDamage = 0;
    public double brainPerDamage = 0;
    public double bloodPerDamage = 0;

    /** Падение: урон, начиная с которого страдают живот и голова. */
    public double highFallDamage = 10.0;

    public InjuryProfile() {}

    public InjuryProfile(String id, WoundType wound, Location location) {
        this.id = id;
        this.wound = wound;
        this.location = location;
    }
}
