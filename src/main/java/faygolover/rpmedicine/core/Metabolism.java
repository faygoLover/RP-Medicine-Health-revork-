package faygolover.rpmedicine.core;

/**
 * Хронические состояния от перков (ТЗ третьего этапа, п. 8): сахар крови у диабетика, повреждённые с
 * начала лёгкие курильщика и печень алкоголика. Вещества и зависимости — аддоном (решения, п. 1.14).
 */
public final class Metabolism {
    private Metabolism() {}

    public static final double NORMAL_SUGAR = 5.5;

    public static void tick(MedicalState m, StepInput in, MedicalSettings s) {
        double hours = in.dt / 3600.0;
        // Курильщик и алкоголик: орган не восстанавливается ниже своего уровня.
        if (in.traits.smoker && m.hasOrgan(Organ.LUNGS)) m.organs[Organ.LUNGS.ordinal()] = Math.max(m.organs[Organ.LUNGS.ordinal()], s.smokerLungsFloor);
        if (in.traits.alcoholic && m.hasOrgan(Organ.LIVER)) m.organs[Organ.LIVER.ordinal()] = Math.max(m.organs[Organ.LIVER.ordinal()], s.alcoholicLiverFloor);

        if (!in.traits.diabetic) {
            // Без диабета сахар держится в норме.
            m.bloodSugar += (NORMAL_SUGAR - m.bloodSugar) * Math.min(1, hours * 10);
            return;
        }
        if (!in.online) return;
        double insulin = m.effect(DrugEffect.INSULIN);
        m.bloodSugar = Physiology.clamp(m.bloodSugar - (s.sugarDeclinePerHour + insulin) * hours, 1.0, 35.0);
        // Гипогликемия глубже 2,5 — мозг страдает.
        if (m.bloodSugar < s.sugarBrainDamage && m.down != MedicalState.Down.CLINICAL)
            m.brain = Math.max(0, m.brain - s.hypoglycemiaBrainPerHour * hours);
        // Высокий сахар — жажда и тошнота.
        if (m.bloodSugar > s.sugarHigh) {
            m.nauseaSeconds = Math.max(m.nauseaSeconds, 20);
            m.thirst = Math.max(0, m.thirst - s.hyperglycemiaThirstPerHour * hours);
        }
    }

    /** Ограничение сознания сахаром: обморок от гипогликемии, кома от высокого сахара. */
    public static double consciousnessCap(MedicalState m, MedicalSettings s) {
        if (m.bloodSugar < s.sugarFaint) return 10;
        if (m.bloodSugar > s.sugarComa) return 5;
        if (m.bloodSugar < s.sugarLow) return 75;
        return 100;
    }

    /** Еда поднимает сахар диабетика по питательности. */
    public static void eat(MedicalState m, int nutrition, MedicalSettings s) {
        m.bloodSugar = Math.min(35, m.bloodSugar + nutrition * s.sugarPerNutrition);
    }
}
