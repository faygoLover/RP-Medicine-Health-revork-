package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Prosthesis;

import java.util.random.RandomGenerator;

/**
 * Некроз, ампутация и протезы (ТЗ третьего этапа, п. 5–6). Некроз — шкала части 0–100: растёт от ишемии
 * 100 (забытый жгут), иссушения и тяжёлого сепсиса; до порога обратим (антибиотик и снятая причина), выше —
 * только ампутация. Отсутствующая часть не принимает урон: попадание уходит в соседнюю.
 */
public final class Limbs {
    private Limbs() {}

    // ------------------------------------------------------------------ некроз

    public static boolean irreversible(BodyPartState ps, MedicalSettings s) {
        return ps.necrosis >= s.necrosisIrreversible;
    }

    public static void tickNecrosis(MedicalState m, StepInput in, MedicalSettings s) {
        if (!in.online) return;
        double dt = in.dt;
        RandomGenerator rnd = in.random;
        boolean antibiotic = m.effect(DrugEffect.ANTIBIOTIC) > 0;
        // Иссушение: начинается на случайной конечности, если некроза ещё нет.
        if (in.withering) {
            boolean any = false;
            for (BodyPartState ps : m.parts) if (ps.necrosis > 0 && !ps.missing) any = true;
            if (!any) {
                BodyPart p = BodyPart.VALUES[3 + rnd.nextInt(6)];
                if (!m.part(p).missing) m.part(p).necrosis = 1;
            }
        }
        for (BodyPartState ps : m.parts) {
            if (!ps.part.isLimb() || ps.missing) continue;
            // Тяжёлый сепсис: стопы и руки могут начать отмирать.
            if (ps.necrosis <= 0 && m.sepsis >= s.necrosisSepsisThreshold && (ps.part.kind == BodyPart.Kind.FOOT || ps.part.isArm())
                    && rnd.nextDouble() < s.necrosisSepsisChancePerHour * dt / 3600.0) ps.necrosis = 1;
            if (ps.ischemia >= 100 && ps.necrosis <= 0) ps.necrosis = 1;
            if (ps.necrosis <= 0) continue;
            boolean cause = ps.ischemia >= 100 || in.withering || m.sepsis >= s.necrosisSepsisThreshold;
            double rate;
            if (cause) rate = s.necrosisGrowthPerHour;
            else if (irreversible(ps, s)) rate = s.necrosisSelfGrowthPerHour;
            else rate = antibiotic ? -s.necrosisRecoveryPerHour : 0;
            ps.necrosis = Physiology.clamp(ps.necrosis + rate * dt / 3600.0, 0, 100);
        }
    }

    /** Необратимый некроз — источник сепсиса, +% в час за каждую такую часть. */
    public static int necroticParts(MedicalState m, MedicalSettings s) {
        int n = 0;
        for (BodyPartState ps : m.parts) if (!ps.missing && irreversible(ps, s)) n++;
        return n;
    }

    /** Боль некроза: сначала сильная, с отмиранием — онемение. */
    public static double necrosisPain(BodyPartState ps) {
        if (ps.necrosis <= 0 || ps.missing) return 0;
        return ps.necrosis < 50 ? Math.min(60, 10 + ps.necrosis * 1.2) : Math.max(0, (100 - ps.necrosis) * 0.8);
    }

    // ------------------------------------------------------------------ отсутствующие части

    /** Ближе к телу: стопа → нога, нога → живот, рука → грудь. */
    public static BodyPart proximal(BodyPart p) {
        return switch (p) {
            case RIGHT_FOOT -> BodyPart.RIGHT_LEG;
            case LEFT_FOOT -> BodyPart.LEFT_LEG;
            case RIGHT_LEG, LEFT_LEG -> BodyPart.ABDOMEN;
            case RIGHT_ARM, LEFT_ARM -> BodyPart.CHEST;
            default -> p;
        };
    }

    /** Часть, которая примет попадание: отсутствующая передаёт его ближе к телу. */
    public static BodyPart present(MedicalState m, BodyPart p) {
        for (int i = 0; i < 3 && m.part(p).missing; i++) p = proximal(p);
        return p;
    }

    /** Отнять часть: нога — вместе со стопой. Культя — рана на самой части. */
    public static void removePart(MedicalState m, BodyPart p) {
        BodyPartState ps = m.part(p);
        ps.clear();
        ps.missing = true;
        if (p.kind == BodyPart.Kind.LEG) {
            BodyPartState foot = m.part(p.pairedLowerLimb());
            foot.clear();
            foot.missing = true;
        }
    }

    /** Хирургическая ампутация: культя под швами, без артериального кровотечения. */
    public static Treatments.Result amputate(MedicalState m, BodyPart p, MedicalSettings s, double quality) {
        removePart(m, p);
        Wound stump = new Wound(WoundType.CUT, s.stumpSeverity);
        stump.surgical = true;
        stump.sutured = true;
        stump.sutureQuality = 0.6 + 0.4 * quality;
        stump.infectionStage = Wound.Infection.CLEAN;
        m.part(p).wounds.add(stump);
        return Treatments.Result.ok("amputated");
    }

    /** Травматическая ампутация: рваная культя, артерия, острая боль. */
    public static void traumatic(MedicalState m, BodyPart p, MedicalSettings s) {
        removePart(m, p);
        BodyPartState ps = m.part(p);
        ps.wounds.add(new Wound(WoundType.CUT, s.traumaticStumpSeverity));
        ps.arterial = true;
        m.painSpike(s.traumaticAmputationPain, 60);
    }

    /** Культя зажила: ран на части почти не осталось. */
    public static boolean stumpHealed(BodyPartState ps) {
        if (!ps.missing) return false;
        for (Wound w : ps.wounds) if (w.severity > 2) return false;
        return !ps.arterial;
    }

    /** Какой протез на какую часть. */
    public static boolean fits(Prosthesis pr, BodyPart p) {
        return switch (pr) {
            case FOOT -> p.kind == BodyPart.Kind.FOOT;
            case PEG_LEG -> p.kind == BodyPart.Kind.LEG;
            case HOOK -> p.isArm();
            default -> false;
        };
    }
}
