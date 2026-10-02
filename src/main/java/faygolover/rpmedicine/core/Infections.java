package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.StepResult.Event;
import faygolover.rpmedicine.core.Wound.Infection;

import java.util.random.RandomGenerator;

/**
 * Инфекция ран, иммунитет, сепсис, температура тела (ТЗ второго этапа, п. 5). Чистая логика над
 * {@link MedicalState}, вызывается шагом физиологии. Инфекция идёт только по времени в сети.
 */
public final class Infections {
    private Infections() {}

    public static void tick(MedicalState m, StepInput in, MedicalSettings s, StepResult r) {
        double maxInfection = 0;
        if (s.infectionEnabled && in.online) maxInfection = tickWounds(m, in, s, r);
        else maxInfection = maxInfection(m);
        if (in.online) tickSepsis(m, in, s);
        tickTemperature(m, in, s, maxInfection);
    }

    // ------------------------------------------------------------------ раны

    private static double tickWounds(MedicalState m, StepInput in, MedicalSettings s, StepResult r) {
        double dt = in.dt;
        RandomGenerator rnd = in.random;
        double immunity = immunityFactor(m, in, s);
        double antibiotic = m.effect(DrugEffect.ANTIBIOTIC);
        double maxInfection = 0;
        double limit = s.foreignBodyInfectionHours * 3600.0;
        for (BodyPartState ps : m.parts) {
            // Пуля или осколок внутри слишком долго: чистые раны от них проверяются ещё раз, шанс выше.
            if (ps.hasForeignBodies()) {
                double before = ps.foreignBodySeconds;
                ps.foreignBodySeconds += dt;
                if (before < limit && ps.foreignBodySeconds >= limit) {
                    for (Wound w : ps.wounds) {
                        if ((w.type == WoundType.GUNSHOT || w.type == WoundType.SHRAPNEL) && w.infectionStage == Infection.CLEAN) {
                            w.infectionStage = Infection.PENDING;
                            w.infectionTimer = 0;
                            w.infectionRisk *= s.foreignBodyInfectionFactor;
                        }
                    }
                }
            } else {
                ps.foreignBodySeconds = 0;
            }
            for (Wound w : ps.wounds) {
                switch (w.infectionStage) {
                    case NEW -> {
                        if (s.infectionChance.getOrDefault(w.type, 0.0) > 0) {
                            w.infectionStage = Infection.PENDING;
                            w.infectionTimer = Physiology.lerp(s.infectionCheckMinMinutes, s.infectionCheckMaxMinutes, rnd.nextDouble()) * 60.0;
                        } else {
                            w.infectionStage = Infection.CLEAN;
                        }
                    }
                    case PENDING -> {
                        w.infectionTimer -= dt;
                        if (w.infectionTimer <= 0) {
                            if (rnd.nextDouble() < infectionChance(w, in, s)) {
                                w.infectionStage = Infection.INFECTED;
                                w.infection = 5;
                                w.immuneProgress = 0;
                                r.add(Event.WOUND_INFECTED);
                            } else {
                                w.infectionStage = Infection.CLEAN;
                            }
                        }
                    }
                    case INFECTED -> {
                        w.infection += (s.infectionGrowthPerHour - antibiotic) * dt / 3600.0;
                        w.immuneProgress += s.immuneGrowthPerHour * immunity * dt / 3600.0;
                        if (w.immuneProgress >= 100 || w.infection <= 0) {
                            w.infectionStage = Infection.CLEAN;
                            w.infection = 0;
                            w.immuneProgress = 0;
                        } else {
                            w.infection = Math.min(100, w.infection);
                            maxInfection = Math.max(maxInfection, w.infection);
                        }
                    }
                    default -> { }
                }
            }
        }
        return maxInfection;
    }

    /** Шанс заражения раны при проверке. */
    public static double infectionChance(Wound w, StepInput in, MedicalSettings s) {
        double c = s.infectionChance.getOrDefault(w.type, 0.0) * w.infectionRisk * in.infectionRiskFactor;
        if (w.isDressed()) c *= 1 - s.dressingInfectionReduction * w.dressingQuality;
        return Physiology.clamp(c, 0, 1);
    }

    /** Сила иммунитета: база 1, кровопотеря, голод, сепсис, койка. */
    public static double immunityFactor(MedicalState m, StepInput in, MedicalSettings s) {
        double f = in.immunityFactor;
        if (m.bloodFraction(s) < 1 - s.immunityBloodLossFraction) f *= s.immunityBloodLossFactor;
        if (m.sepsis >= 30) f *= s.immunitySepsisFactor;
        return f;
    }

    private static double maxInfection(MedicalState m) {
        double max = 0;
        for (BodyPartState ps : m.parts) for (Wound w : ps.wounds) if (w.isInfected()) max = Math.max(max, w.infection);
        return max;
    }

    /** Ран с инфекцией 100 % — источников сепсиса. */
    public static int sepsisSources(MedicalState m) {
        int n = 0;
        for (BodyPartState ps : m.parts) for (Wound w : ps.wounds) if (w.isInfected() && w.infection >= 100) n++;
        return n;
    }

    // ------------------------------------------------------------------ сепсис

    private static void tickSepsis(MedicalState m, StepInput in, MedicalSettings s) {
        double dt = in.dt;
        double rate = sepsisSources(m) * s.sepsisPerHourPerSource;
        if (m.spoiledBloodSeconds > 0) {
            rate += s.spoiledBloodSepsisPerHour;
            m.spoiledBloodSeconds = Math.max(0, m.spoiledBloodSeconds - dt);
        }
        double antibiotic = m.effect(DrugEffect.ANTIBIOTIC);
        if (antibiotic > 0) rate -= s.sepsisAntibioticDeclinePerHour * antibiotic / 10.0;
        // Без антибиотика и без источника сам проходит только лёгкий сепсис; тяжёлый держится до лечения.
        else if (rate <= 0) rate = m.sepsis < 30 ? -s.sepsisNaturalDeclinePerHour : 0;
        if (m.sepsis <= 0 && rate <= 0) return;
        m.sepsis = Physiology.clamp(m.sepsis + rate * dt / 3600.0, 0, 100);
    }

    /** Испорченная кровь попала в вену: сразу сильное заражение и рост сепсиса ещё несколько часов. */
    public static void spoiledBlood(MedicalState m, MedicalSettings s) {
        m.sepsis = Math.max(m.sepsis, s.spoiledBloodSepsis);
        m.spoiledBloodSeconds = Math.max(m.spoiledBloodSeconds, s.spoiledBloodSepsisHours * 3600.0);
    }

    // ------------------------------------------------------------------ температура

    /** Лихорадка без жаропонижающего, °C над нормой. */
    public static double fever(MedicalState m, MedicalSettings s, double maxInfection) {
        double f = s.localInfectionFever * maxInfection / 100.0;
        if (m.sepsis >= 10) f = Math.max(f, Physiology.lerp(s.sepsisFeverMin, s.sepsisFeverMax, (m.sepsis - 10) / 90.0));
        if (m.transfusionReactionSeconds > 0) f = Math.max(f, s.transfusionReactionFever);
        return f;
    }

    private static void tickTemperature(MedicalState m, StepInput in, MedicalSettings s, double maxInfection) {
        double fever = fever(m, s, maxInfection) * (1 - Physiology.clamp(m.effect(DrugEffect.ANTIPYRETIC), 0, 1));
        double target = s.normalBodyTemp + fever + in.ambientTempShift;
        double step = s.bodyTempChangePerMinute * in.dt / 60.0;
        double d = target - m.bodyTemp;
        if (Math.abs(d) <= step) m.bodyTemp = target;
        else m.bodyTemp += Math.signum(d) * step;
    }
}
