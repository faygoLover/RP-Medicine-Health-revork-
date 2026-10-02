package faygolover.rpmedicine.core;

/**
 * Стойкость организма из перков RP Perks (п. 6.3 ТЗ). Навыков у пациента нет.
 * Собирается слоем Minecraft из перков; здесь только числа.
 */
public final class PatientTraits {
    public static final PatientTraits NONE = new PatientTraits(false, false, false, false, false);

    /** «Живучий», «Крепкий». */
    public final boolean tough;
    /** «Хрупкий», «Задохлик», «Слабак». */
    public final boolean fragile;
    /** «Храбрый», «Волевой». */
    public final boolean brave;
    /** «Трусливый». */
    public final boolean coward;
    /** Леворукий: рабочая рука — левая. */
    public final boolean leftHanded;

    public PatientTraits(boolean tough, boolean fragile, boolean brave, boolean coward, boolean leftHanded) {
        this.tough = tough;
        this.fragile = fragile;
        this.brave = brave;
        this.coward = coward;
        this.leftHanded = leftHanded;
    }

    public double shockThreshold(MedicalSettings s) {
        double t = s.painShockThreshold;
        if (tough) t += s.toughShockBonus;
        if (brave) t += s.braveShockBonus;
        if (coward) t -= s.cowardShockPenalty;
        if (fragile) t -= s.fragilePainThresholdPenalty;
        return Math.max(40, Math.min(100, t));
    }

    public double bleedFactor(MedicalSettings s) {
        return tough ? s.toughBleedFactor : 1.0;
    }

    public double knockdownFactor(MedicalSettings s) {
        return tough ? s.toughKnockdownFactor : 1.0;
    }

    public double fractureFactor(MedicalSettings s) {
        return fragile ? s.fragileFractureFactor : 1.0;
    }

    public BodyPart workingArm() {
        return leftHanded ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
    }
}
