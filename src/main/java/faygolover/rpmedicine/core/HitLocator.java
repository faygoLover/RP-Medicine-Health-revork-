package faygolover.rpmedicine.core;

/**
 * Определение части тела по точке удара (п. 2.2 ТЗ). Слой Minecraft переводит точку удара в
 * координаты тела: высота от ступней (доля роста), смещение вбок от оси тела (доля половины ширины,
 * плюс — правая сторона тела) и поза.
 */
public final class HitLocator {
    private HitLocator() {}

    public enum Posture {
        STANDING, CROUCHING,
        /** Тело горизонтально (плавание, ползком, лежачий): «высота» — положение вдоль тела от ног к голове. */
        HORIZONTAL
    }

    // Границы по высоте (доля роста) для стоящего.
    public static double feetTop = 0.10;
    public static double legsTop = 0.45;
    public static double abdomenTop = 0.60;
    public static double chestTop = 0.80;
    /** Насколько далеко от оси тела (доля половины ширины) на высоте туловища удар считается в руку. */
    public static double armLateral = 0.6;

    /**
     * @param height  0 — ступни, 1 — макушка (для горизонтальной позы — от ног к голове вдоль тела)
     * @param lateral от −1 (левый край) до 1 (правый край тела)
     */
    public static BodyPart locate(double height, double lateral, Posture posture) {
        double h = Math.max(0, Math.min(1, height));
        double feet = feetTop;
        double legs = legsTop;
        double abd = abdomenTop;
        double chest = chestTop;
        if (posture == Posture.CROUCHING) {
            // В приседе ноги согнуты: туловище и голова занимают большую долю высоты.
            feet = 0.12;
            legs = 0.38;
            abd = 0.55;
            chest = 0.77;
        } else if (posture == Posture.HORIZONTAL) {
            // Лёжа ноги и туловище примерно одной длины вдоль тела.
            feet = 0.08;
            legs = 0.42;
            abd = 0.58;
            chest = 0.82;
        }
        boolean right = lateral >= 0;
        if (h < feet) return right ? BodyPart.RIGHT_FOOT : BodyPart.LEFT_FOOT;
        if (h < legs) return right ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
        if (h < chest) {
            if (Math.abs(lateral) > armLateral) return right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
            return h < abd ? BodyPart.ABDOMEN : BodyPart.CHEST;
        }
        return BodyPart.HEAD;
    }
}
