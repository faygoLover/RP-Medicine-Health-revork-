package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Fracture;
import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Pneumo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.random.RandomGenerator;

/** Превращение урона в раны и осложнения (п. 3 ТЗ). */
public final class Injuries {
    private Injuries() {}

    /** Что получилось от одного попадания по части тела. */
    public enum Outcome {
        WOUND, FRACTURE, OPEN_FRACTURE, RIB_FRACTURE, ARTERIAL, INTERNAL, FOREIGN_BODY, CONCUSSION,
        KNOCKOUT, PNEUMOTHORAX, DRESSING_REOPENED, INSTANT_DEATH
    }

    public static final class Report {
        public final EnumSet<Outcome> outcomes = EnumSet.noneOf(Outcome.class);
        public final List<BodyPart> parts = new ArrayList<>();
        public double totalSeverity;

        public boolean has(Outcome o) {
            return outcomes.contains(o);
        }

        void merge(Report o) {
            outcomes.addAll(o.outcomes);
            for (BodyPart p : o.parts) if (!parts.contains(p)) parts.add(p);
            totalSeverity += o.totalSeverity;
        }
    }

    /** Доля урона по частям тела. */
    public record Share(BodyPart part, double fraction) {}

    /**
     * Добавляет рану: одинаковые раны без повязки на одной части сливаются (тяжесть складывается),
     * записей не больше {@code maxWoundsPerPart}. Возвращает рану, в которую попал урон.
     */
    public static Wound mergeWound(BodyPartState ps, WoundType type, double severity, MedicalSettings s) {
        if (severity <= 0) return null;
        for (Wound w : ps.wounds) {
            if (w.type == type && !w.isDressed()) {
                w.severity = Wound.clampSeverity(w.severity + severity);
                w.peakSeverity = w.severity;
                w.clot = 0;
                return w;
            }
        }
        if (ps.wounds.size() < s.maxWoundsPerPart) {
            Wound w = new Wound(type, severity);
            ps.wounds.add(w);
            return w;
        }
        // Места нет: сливаем с такой же раной (повязка слетает) или с самой тяжёлой.
        Wound target = null;
        for (Wound w : ps.wounds) if (w.type == type) { target = w; break; }
        if (target == null) {
            for (Wound w : ps.wounds) if (target == null || w.severity > target.severity) target = w;
        }
        target.severity = Wound.clampSeverity(target.severity + severity);
        target.peakSeverity = target.severity;
        target.clot = 0;
        if (target.dressing != Dressing.HEMOSTATIC) target.removeDressing();
        return target;
    }

    /**
     * Попадание по одной части тела. {@code damage} — урон после брони, единицы здоровья.
     * Обрабатывает осложнения, адреналин, урон по лежачему.
     */
    public static Report hitPart(MedicalState m, BodyPart part, double damage, InjuryProfile prof, PatientTraits traits,
                                 RandomGenerator rnd, MedicalSettings s) {
        Report rep = new Report();
        if (damage <= 0 || prof.wound == null) return rep;
        double sev = damage * s.severityPerDamage * prof.severityMultiplier;
        if (sev <= 0) return rep;
        BodyPartState ps = m.part(part);
        rep.parts.add(part);
        rep.totalSeverity += sev;

        // Новый урон может открыть свежие повязки на этой части.
        for (Wound w : ps.wounds) {
            if (w.isFreshDressing(s) && rnd.nextDouble() < s.reopenChanceOnDamage) {
                w.removeDressing();
                rep.outcomes.add(Outcome.DRESSING_REOPENED);
            }
        }
        mergeWound(ps, prof.wound, sev, s);
        rep.outcomes.add(Outcome.WOUND);

        // Перелом: рёбра в груди, кости рук, ног, стоп.
        if (s.fracturesEnabled && (part.isLimb() || part == BodyPart.CHEST) && !ps.hasFracture()) {
            double ch = prof.fracture.at(sev) * traits.fractureFactor(s);
            if (rnd.nextDouble() < ch) {
                if (part == BodyPart.CHEST) {
                    ps.fracture = Fracture.CLOSED;
                    rep.outcomes.add(Outcome.RIB_FRACTURE);
                } else if (rnd.nextDouble() < prof.openFractureFraction) {
                    ps.fracture = Fracture.OPEN;
                    rep.outcomes.add(Outcome.OPEN_FRACTURE);
                } else {
                    ps.fracture = Fracture.CLOSED;
                    rep.outcomes.add(Outcome.FRACTURE);
                }
                ps.fractureHeal = 0;
            }
        } else if (s.fracturesEnabled && part.isLimb() && ps.fracture == Fracture.CLOSED
                && rnd.nextDouble() < prof.fracture.at(sev) * prof.openFractureFraction) {
            // Новый удар по сломанной кости может сделать перелом открытым.
            ps.fracture = Fracture.OPEN;
            rep.outcomes.add(Outcome.OPEN_FRACTURE);
        }

        // Артериальное кровотечение: руки, ноги, голова (шея).
        if (!ps.arterial && (part.isArm() || part.kind == BodyPart.Kind.LEG || part == BodyPart.HEAD)
                && rnd.nextDouble() < prof.arterial.at(sev)) {
            ps.arterial = true;
            rep.outcomes.add(Outcome.ARTERIAL);
        }

        // Внутреннее кровотечение: грудь и живот.
        if (part.isTorso() && rnd.nextDouble() < prof.internal.at(sev)) {
            ps.internalBleed = Math.min(s.internalBleedMax, ps.internalBleed + sev * s.internalBleedPerSeverity);
            rep.outcomes.add(Outcome.INTERNAL);
        }

        // Инородные тела: слепое огнестрельное, осколки.
        if (rnd.nextDouble() < prof.foreignBody.at(sev)) {
            int n = prof.foreignBodyMin + (prof.foreignBodyMax > prof.foreignBodyMin
                    ? rnd.nextInt(prof.foreignBodyMax - prof.foreignBodyMin + 1) : 0);
            if (n > 0) {
                if (prof.wound == WoundType.GUNSHOT) ps.bullets += n;
                else ps.fragments += n;
                rep.outcomes.add(Outcome.FOREIGN_BODY);
            }
        }

        // Контузия.
        if (s.concussionEnabled && rnd.nextDouble() < prof.concussion.at(sev)) {
            if (addConcussion(m, sev * prof.concussionPerSeverity, rnd, s)) rep.outcomes.add(Outcome.KNOCKOUT);
            rep.outcomes.add(Outcome.CONCUSSION);
        }

        // Проникающая рана груди — открытый пневмоторакс.
        if (s.pneumothoraxEnabled && part == BodyPart.CHEST && prof.wound.isPenetrating()
                && m.pneumo == Pneumo.NONE && rnd.nextDouble() < prof.pneumothorax.at(sev)) {
            m.pneumo = Pneumo.OPEN;
            m.pneumoTimer = Physiology.lerp(s.pneumoSealMinSeconds, s.pneumoSealMaxSeconds, rnd.nextDouble());
            m.tensionProgress = 0;
            rep.outcomes.add(Outcome.PNEUMOTHORAX);
        }
        return rep;
    }

    /** Контузия: копится, при сильной — короткая потеря сознания. Возвращает true при потере сознания. */
    public static boolean addConcussion(MedicalState m, double amount, RandomGenerator rnd, MedicalSettings s) {
        if (!s.concussionEnabled || amount <= 0) return false;
        m.concussion = Math.min(100, m.concussion + amount);
        if (m.concussion >= s.concussionKnockoutLevel && m.concussionKoSeconds <= 0) {
            m.concussionKoSeconds = Physiology.lerp(s.concussionKnockoutMinSeconds, s.concussionKnockoutMaxSeconds, rnd.nextDouble());
            return true;
        }
        return false;
    }

    /**
     * Общий вход: урон {@code damage} по профилю. Для {@link InjuryProfile.Location#HIT_POINT} часть
     * задаёт вызывающий ({@code hitPart}); для остальных способов часть выбирается здесь.
     */
    public static Report apply(MedicalState m, InjuryProfile prof, double damage, BodyPart hitPart, double explosionSideRight,
                               PatientTraits traits, RandomGenerator rnd, MedicalSettings s) {
        Report total = new Report();
        if (damage <= 0) return total;
        if (prof.wound == null || prof.location == InjuryProfile.Location.NONE) {
            applyPhysiologyOnly(m, prof, damage, s);
        } else {
            for (Share sh : distribute(prof, damage, hitPart, explosionSideRight, rnd)) {
                total.merge(hitPart(m, sh.part, damage * sh.fraction, prof, traits, rnd, s));
            }
        }
        afterInjury(m, total, damage, s, rnd);
        return total;
    }

    /** Утопление, голод, магия: без ран, прямо на физиологию. */
    public static void applyPhysiologyOnly(MedicalState m, InjuryProfile prof, double damage, MedicalSettings s) {
        if (prof.spo2PerDamage != 0) m.spo2 = Physiology.clamp(m.spo2 - prof.spo2PerDamage * damage, 0, 100);
        if (prof.brainPerDamage != 0) m.brain = Math.max(m.down == Down.CLINICAL ? 1 : 0.5, m.brain - prof.brainPerDamage * damage);
        if (prof.bloodPerDamage != 0) m.bloodVolume = Math.max(0, m.bloodVolume - prof.bloodPerDamage * damage);
    }

    /** Адреналин при ранении и сокращение таймера у лежачего. */
    static void afterInjury(MedicalState m, Report rep, double damage, MedicalSettings s, RandomGenerator rnd) {
        if (rep.totalSeverity <= 0) return;
        if (m.down == Down.NONE) {
            double t = Physiology.lerp(s.adrenalineMinSeconds, s.adrenalineMaxSeconds, Physiology.clamp(rep.totalSeverity / 40.0, 0, 1));
            m.adrenalineSeconds = Math.max(m.adrenalineSeconds, t);
        } else if (m.down == Down.KNOCKDOWN || m.down == Down.FAINT) {
            m.brain = Math.max(0, m.brain - damage * s.downedDamageBrainPerDamage);
        }
    }

    /** Как делится урон между частями тела для способов, отличных от точки попадания. */
    public static List<Share> distribute(InjuryProfile prof, double damage, BodyPart hitPart, double sideRight, RandomGenerator rnd) {
        List<Share> out = new ArrayList<>();
        switch (prof.location) {
            case HIT_POINT -> out.add(new Share(hitPart != null ? hitPart : randomPart(rnd), 1.0));
            case HEAD -> out.add(new Share(BodyPart.HEAD, 1.0));
            case CHEST -> out.add(new Share(BodyPart.CHEST, 1.0));
            case LEGS -> {
                BodyPart leg = rnd.nextBoolean() ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
                out.add(new Share(leg, 0.6));
                out.add(new Share(leg.pairedLowerLimb(), 0.4));
            }
            case FALL -> {
                if (damage >= prof.highFallDamage) {
                    out.add(new Share(BodyPart.RIGHT_FOOT, 0.22));
                    out.add(new Share(BodyPart.LEFT_FOOT, 0.22));
                    out.add(new Share(BodyPart.RIGHT_LEG, 0.16));
                    out.add(new Share(BodyPart.LEFT_LEG, 0.16));
                    out.add(new Share(BodyPart.ABDOMEN, 0.14));
                    out.add(new Share(BodyPart.HEAD, 0.10));
                } else {
                    out.add(new Share(BodyPart.RIGHT_FOOT, 0.3));
                    out.add(new Share(BodyPart.LEFT_FOOT, 0.3));
                    out.add(new Share(BodyPart.RIGHT_LEG, 0.2));
                    out.add(new Share(BodyPart.LEFT_LEG, 0.2));
                }
            }
            case EXPLOSION -> {
                int n = 2 + (int) Math.min(3, damage / 6.0);
                List<BodyPart> chosen = new ArrayList<>();
                for (int i = 0; i < n * 4 && chosen.size() < n; i++) {
                    BodyPart p = weightedExplosionPart(sideRight, rnd);
                    if (!chosen.contains(p)) chosen.add(p);
                }
                for (BodyPart p : chosen) out.add(new Share(p, 1.0 / chosen.size()));
            }
            case FIRE -> {
                BodyPart p = randomPart(rnd);
                out.add(new Share(p, 1.0));
            }
            case LAVA -> {
                List<BodyPart> chosen = new ArrayList<>();
                int n = 3 + rnd.nextInt(2);
                for (int i = 0; i < 20 && chosen.size() < n; i++) {
                    BodyPart p = rnd.nextDouble() < 0.6 ? lowerPart(rnd) : randomPart(rnd);
                    if (!chosen.contains(p)) chosen.add(p);
                }
                for (BodyPart p : chosen) out.add(new Share(p, 1.0 / chosen.size()));
            }
            case RANDOM -> out.add(new Share(randomPart(rnd), 1.0));
            case NONE -> { }
        }
        return out;
    }

    /** Случайная часть с весом по площади: туловище и ноги чаще. */
    public static BodyPart randomPart(RandomGenerator rnd) {
        double[] w = {0.08, 0.2, 0.14, 0.1, 0.1, 0.13, 0.13, 0.06, 0.06};
        double x = rnd.nextDouble();
        for (int i = 0; i < w.length; i++) {
            x -= w[i];
            if (x <= 0) return BodyPart.VALUES[i];
        }
        return BodyPart.CHEST;
    }

    private static BodyPart lowerPart(RandomGenerator rnd) {
        BodyPart[] lower = {BodyPart.RIGHT_FOOT, BodyPart.LEFT_FOOT, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG};
        return lower[rnd.nextInt(lower.length)];
    }

    /** Часть, обращённая к взрыву: {@code sideRight} от −1 (слева) до 1 (справа). */
    private static BodyPart weightedExplosionPart(double sideRight, RandomGenerator rnd) {
        double[] w = new double[BodyPart.VALUES.length];
        for (BodyPart p : BodyPart.VALUES) {
            double base = switch (p.kind) {
                case HEAD -> 0.8;
                case TORSO -> 1.5;
                default -> 1.0;
            };
            if (p.side == BodyPart.Side.RIGHT) base *= 1 + sideRight * 0.75;
            else if (p.side == BodyPart.Side.LEFT) base *= 1 - sideRight * 0.75;
            w[p.ordinal()] = Math.max(0.05, base);
        }
        double sum = 0;
        for (double v : w) sum += v;
        double x = rnd.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            x -= w[i];
            if (x <= 0) return BodyPart.VALUES[i];
        }
        return BodyPart.CHEST;
    }

    /** Мгновенно смертельное попадание (п. 1.2 решений): выстрел в голову выше порога или огромный урон. */
    public static boolean isInstantlyLethal(BodyPart part, double damage, MedicalSettings s) {
        if (s.instantDeathDamage > 0 && damage >= s.instantDeathDamage) return true;
        return part == BodyPart.HEAD && s.instantDeathHeadDamage > 0 && damage >= s.instantDeathHeadDamage;
    }
}
