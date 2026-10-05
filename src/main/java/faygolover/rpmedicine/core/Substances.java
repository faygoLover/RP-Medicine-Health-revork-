package faygolover.rpmedicine.core;

import java.util.random.RandomGenerator;

/**
 * Толерантность, зависимость, ломка и опьянение (ТЗ третьего этапа, п. 9; по [RW] и [NT]).
 * <ul>
 * <li>Толерантность 0–100: каждая доза прибавляет, в сети спадает; лекарства с веществом действуют короче.</li>
 * <li>Зависимость: шанс за дозу = база × (1 + 4 × толерантность); пропадает, когда толерантность ниже 10.</li>
 * <li>Ломка: зависимость и долго нет вещества — тяга, пот, тошнота, боль, замедление; у алкоголя — судороги.</li>
 * <li>Опьянение (алкоголь) 0–100: прицел качает, речь невнятная, сильное — сознание ниже, рвота.</li>
 * </ul>
 */
public final class Substances {
    private Substances() {}

    public static double tolerance(MedicalState m, Substance x) {
        return m.tolerance[x.ordinal()];
    }

    public static boolean dependent(MedicalState m, Substance x) {
        return (m.dependence & x.bit()) != 0;
    }

    /** Ломка сейчас: зависимость и вещества не было дольше порога. */
    public static boolean withdrawal(MedicalState m, Substance x, MedicalSettings s) {
        return dependent(m, x) && m.sinceDose[x.ordinal()] >= x.withdrawalHours * 3600.0 * s.withdrawalDelayFactor;
    }

    public static boolean anyWithdrawal(MedicalState m, MedicalSettings s) {
        for (Substance x : Substance.VALUES) if (withdrawal(m, x, s)) return true;
        return false;
    }

    /** Множитель длительности действия лекарства с веществом: ×(1 − 0,5 × толерантность). */
    public static double durationFactor(MedicalState m, Substance x) {
        return x == null ? 1 : 1 - 0.5 * tolerance(m, x) / 100.0;
    }

    /**
     * Доза вещества ({@code amount} — в «стандартных дозах»). {@code traits} — для «Непьющего».
     */
    public static void dose(MedicalState m, Substance x, double amount, PatientTraits traits, RandomGenerator rnd, MedicalSettings s) {
        if (amount <= 0 || !s.substancesEnabled) return;
        int i = x.ordinal();
        double tol = m.tolerance[i] / 100.0;
        // Зависимость — до прироста толерантности.
        if (!dependent(m, x) && rnd.nextDouble() < x.dependenceBase * s.dependenceFactor * (1 + 4 * tol) * amount) m.dependence |= x.bit();
        m.tolerance[i] = Math.min(100, m.tolerance[i] + x.toleranceGain * s.toleranceGainFactor * amount);
        m.sinceDose[i] = 0;
        switch (x) {
            case ALCOHOL -> {
                double k = (1 - 0.6 * tol) * (traits.teetotaler ? 2.0 : 1.0);
                m.intoxication = Math.min(100, m.intoxication + s.alcoholIntoxicationPerDose * amount * k);
            }
            case NICOTINE -> m.addEffect(DrugEffect.ANALGESIA, s.nicotineAnalgesia * amount, 0, 600 * (1 - 0.5 * tol));
            case CAFFEINE -> m.addEffect(DrugEffect.HEART_RATE, 6 * amount, 60, 1800 * (1 - 0.5 * tol));
            case STIMULANT -> {
                m.addEffect(DrugEffect.HEART_RATE, 15 * amount, 30, 1200 * (1 - 0.5 * tol));
                m.addEffect(DrugEffect.ANALGESIA, 10 * amount, 30, 1200 * (1 - 0.5 * tol));
            }
            default -> { }
        }
    }

    /** Налоксон при опиатной зависимости — сразу ломка. */
    public static void precipitateOpioidWithdrawal(MedicalState m, MedicalSettings s) {
        if (!dependent(m, Substance.OPIOID)) return;
        m.sinceDose[Substance.OPIOID.ordinal()] = Substance.OPIOID.withdrawalHours * 3600.0 * s.withdrawalDelayFactor;
        m.painSpike(60, 120);
    }

    public static void tick(MedicalState m, StepInput in, MedicalSettings s) {
        double dt = in.dt;
        double hours = dt / 3600.0;
        // Перки: стартовая толерантность и зависимость.
        if (in.traits.alcoholic) holdAtLeast(m, Substance.ALCOHOL, s.alcoholicTolerance);
        if (in.traits.smoker) holdAtLeast(m, Substance.NICOTINE, s.smokerNicotineTolerance);
        if (in.traits.caffeineAddict) holdAtLeast(m, Substance.CAFFEINE, s.caffeineAddictTolerance);
        // Опьянение уходит и в сети, и вне её (организм работает).
        if (m.intoxication > 0) m.intoxication = Math.max(0, m.intoxication - s.alcoholDecayPerHour * hours);
        if (m.seizureSeconds > 0) m.seizureSeconds = Math.max(0, m.seizureSeconds - dt);
        if (!in.online) return;
        for (Substance x : Substance.VALUES) {
            int i = x.ordinal();
            m.sinceDose[i] += dt;
            if (m.tolerance[i] > 0) m.tolerance[i] = Math.max(0, m.tolerance[i] - s.toleranceDecayPerHour * hours);
            if (dependent(m, x) && m.tolerance[i] < s.dependenceLossTolerance) m.dependence &= ~x.bit();
            if (!withdrawal(m, x, s)) continue;
            // Ломка: боль, тошнота; алкоголь — судороги.
            m.acutePain = Math.max(m.acutePain, s.withdrawalPain);
            m.acutePainSeconds = Math.max(m.acutePainSeconds, 5);
            if (x == Substance.ALCOHOL && m.seizureSeconds <= 0 && in.random.nextDouble() < s.alcoholSeizureChancePerHour * hours)
                m.seizureSeconds = s.seizureSeconds;
        }
        // Сильное опьянение — рвота (сервер играет по флагу тошноты).
        if (m.intoxication >= s.intoxicationVomit) m.nauseaSeconds = Math.max(m.nauseaSeconds, 10);
    }

    private static void holdAtLeast(MedicalState m, Substance x, double tol) {
        int i = x.ordinal();
        if (m.tolerance[i] < tol) m.tolerance[i] = tol;
        m.dependence |= x.bit();
    }

    /** Ограничение сознания: сильное опьянение, судороги. */
    public static double consciousnessCap(MedicalState m, MedicalSettings s) {
        if (m.seizureSeconds > 0) return 5;
        if (m.intoxication >= s.intoxicationPassOut) return 10;
        if (m.intoxication >= s.intoxicationHeavy) return 50;
        return 100;
    }

    /** Время, прокрученное командой {@code time add}: толерантность, ломка, опьянение. */
    public static void advance(MedicalState m, double seconds, MedicalSettings s) {
        double hours = seconds / 3600.0;
        m.intoxication = Math.max(0, m.intoxication - s.alcoholDecayPerHour * hours);
        for (Substance x : Substance.VALUES) {
            int i = x.ordinal();
            m.sinceDose[i] += seconds;
            m.tolerance[i] = Math.max(0, m.tolerance[i] - s.toleranceDecayPerHour * hours);
            if (dependent(m, x) && m.tolerance[i] < s.dependenceLossTolerance) m.dependence &= ~x.bit();
        }
    }

    /** Невнятная речь пьяного: буквы двоятся и проглатываются. */
    public static String slur(String text, double intoxication, RandomGenerator rnd) {
        if (intoxication < 30) return text;
        double p = Math.min(0.35, (intoxication - 30) / 150.0);
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c) && rnd.nextDouble() < p) {
                if (rnd.nextBoolean()) out.append(c).append(c);
                else if ("сшзжчщ".indexOf(Character.toLowerCase(c)) >= 0 || "szc".indexOf(Character.toLowerCase(c)) >= 0) out.append("ш");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
