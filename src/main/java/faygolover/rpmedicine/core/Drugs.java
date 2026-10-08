package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.MedicalState.Down;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Применение препаратов (ТЗ второго этапа, п. 6.1): эффекты с задержкой и длительностью, одинаковые
 * продлеваются по времени; превышение дозы за окно — передозировка со своим эффектом.
 */
public final class Drugs {
    private Drugs() {}

    /** null — можно; иначе ключ отказа {@code rpmedicine.refuse.<key>}. */
    public static String check(MedicalState m, BodyPart part, Drug d, MedicalSettings s) {
        if (d.form() == Drug.Form.PILL && m.down != Down.NONE) return "must_be_conscious";
        if (d.special() == Drug.Special.OPIOID_ANTIDOTE && !opioidsActive(m)) return "no_opioids";
        if (d.form() == Drug.Form.TOPICAL) {
            BodyPartState ps = m.part(part);
            switch (d.special()) {
                case ANTISEPTIC -> {
                    for (Wound w : ps.wounds) if (w.type != WoundType.BRUISE && w.infectionStage != Wound.Infection.CLEAN) return null;
                    return "no_open_wound";
                }
                case ANTIBIOTIC_OINTMENT -> {
                    for (Wound w : ps.wounds) if (w.isInfected()) return superficial(w) ? null : "infection_too_deep";
                    return "no_infection_here";
                }
                default -> {
                    return ps.wounds.isEmpty() ? "no_open_wound" : null;
                }
            }
        }
        return null;
    }

    /** Поверхностная рана: ожог или неглубокая (мазь лечит только такие). */
    public static boolean superficial(Wound w) {
        return w.type == WoundType.BURN || w.severity < 35;
    }

    public static boolean opioidsActive(MedicalState m) {
        if (m.morphineSeconds > 0 || m.morphineOverdoseSeconds > 0 || m.opioidSeconds > 0) return true;
        // Препарат в крови (п. 1.16): опиат ещё всасывается из мышцы или уже в крови — налоксону есть что вытеснять
        // (замечание живого теста 19: при передозировке писал «опиатов нет»).
        for (DrugLevels.Level l : m.drugLevels.values()) {
            Drug d = l.drug != null ? l.drug : DrugLevels.resolver.apply(l.id);
            if (d != null && d.opioid() && l.plasma + l.depot > 0) return true;
        }
        return false;
    }

    /** Препарат местной анестезии (укол в часть тела). */
    public static boolean isLocal(Drug d) {
        for (Drug.Dose dose : d.effects()) if (dose.effect() == DrugEffect.LOCAL_ANESTHESIA) return true;
        return false;
    }

    /** Местная анестезия без указанной части: самая больная часть без анестезии. */
    public static BodyPart localPart(MedicalState m, MedicalSettings s) {
        BodyPart best = BodyPart.CHEST;
        double bestPain = -1;
        for (BodyPartState ps : m.parts) {
            if (ps.localAnesthesiaSeconds > 0) continue;
            double p = Physiology.partPain(m, ps, s) + ps.totalSeverity() * 0.01;
            if (p > bestPain) {
                bestPain = p;
                best = ps.part;
            }
        }
        return best;
    }

    /** Часть тела для наружного средства, где оно нужнее всего (null — нигде). */
    public static BodyPart bestPart(MedicalState m, Drug d, MedicalSettings s) {
        BodyPart best = null;
        double bestScore = -1;
        for (BodyPart p : BodyPart.VALUES) {
            if (check(m, p, d, s) != null) continue;
            double score = m.part(p).totalSeverity();
            if (score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    public static Treatments.Result apply(MedicalState m, BodyPart part, Drug d, boolean error, RandomGenerator rnd, MedicalSettings s) {
        return apply(m, part, d, error, rnd, s, 1.0);
    }

    /**
     * Действующая доза с учётом веса: стандартная доза рассчитана на {@code s.doseReferenceWeight} кг —
     * тяжёлому её мало, лёгкому много (решения, п. 1.13). Наружные средства от веса не зависят.
     */
    public static double effectiveDose(MedicalState m, Drug d, double dose, MedicalSettings s) {
        if (d.form() == Drug.Form.TOPICAL || !s.doseByWeight) return dose;
        double f = Math.pow(s.doseReferenceWeight / Math.max(20, m.weightKg), s.doseWeightExponent);
        return dose * Physiology.clamp(f, 0.5, 1.6);
    }

    public static Treatments.Result apply(MedicalState m, BodyPart part, Drug d, boolean error, RandomGenerator rnd, MedicalSettings s, double amount) {
        return apply(m, part, d, error, rnd, s, amount, DrugLevels.routeOf(d));
    }

    /** Ввести препарат: доза идёт в кровь по пути введения (решения, п. 1.16), дальше действует уровень в крови. */
    public static Treatments.Result apply(MedicalState m, BodyPart part, Drug d, boolean error, RandomGenerator rnd, MedicalSettings s,
                                          double amount, DrugLevels.Route route) {
        double eff = effectiveDose(m, d, amount, s);
        double k = (error ? 0.5 : 1.0) * eff;
        if (d.substance() != null) Substances.dose(m, d.substance(), d.substanceAmount() * amount, PatientTraits.NONE, rnd, s);
        switch (d.special()) {
            case GLUCOSE -> m.bloodSugar = Math.min(35, m.bloodSugar + MedicalSettings.get().glucoseTabletSugar * k);
            case OPIOID_ANTIDOTE -> {
                Substances.precipitateOpioidWithdrawal(m, s);
                m.morphineSeconds = 0;
                m.morphineDelay = 0;
                m.morphineOverdoseSeconds = 0;
                m.opioidSeconds = 0;
                m.effects.remove(DrugEffect.RESP_DEPRESSION);
                m.effects.remove(DrugEffect.ANALGESIA);
                DrugLevels.clearOpioids(m);
                DrugLevels.refresh(m, s, rnd);
                if (m.heart == MedicalState.Heart.NORMAL) m.respiratoryArrest = false;
            }
            case ANTISEPTIC -> {
                for (Wound w : m.part(part).wounds) {
                    if (w.type == WoundType.BRUISE || w.infectionStage == Wound.Infection.CLEAN || w.isInfected()) continue;
                    w.infectionRisk *= error ? (1 + s.antisepticInfectionFactor) / 2 : s.antisepticInfectionFactor;
                }
                return error ? Treatments.Result.failed("antiseptic_poor") : Treatments.Result.ok("antiseptic_applied");
            }
            case ANTIBIOTIC_OINTMENT -> {
                for (Wound w : m.part(part).wounds) {
                    if (!w.isInfected() || !superficial(w)) continue;
                    if (error) {
                        w.infection = Math.max(1, w.infection * 0.5);
                    } else {
                        w.infectionStage = Wound.Infection.CLEAN;
                        w.infection = 0;
                        w.immuneProgress = 0;
                    }
                }
                return error ? Treatments.Result.failed("ointment_partial") : Treatments.Result.ok("ointment_applied");
            }
            default -> { }
        }
        for (Drug.Dose dose : d.effects()) {
            // Местная анестезия — на часть тела, куда уколол (третий этап, п. 3).
            if (dose.effect() == DrugEffect.LOCAL_ANESTHESIA) {
                BodyPartState ps = m.part(part);
                ps.localAnesthesiaSeconds = Math.max(ps.localAnesthesiaSeconds, dose.seconds() * k);
            }
        }
        // Промах — половина дозы мимо.
        DrugLevels.give(m, d, amount * (error ? 0.5 : 1.0), route, s);
        DrugLevels.refresh(m, s, rnd);
        DrugLevels.Level lvl = m.drugLevels.get(d.id());
        if (lvl != null && lvl.plasma + lvl.depot > d.overdoseLevel()) return Treatments.Result.ok("drug_overdose");
        if (d.special() == Drug.Special.OPIOID_ANTIDOTE) return Treatments.Result.ok("antidote_given");
        if (error) return Treatments.Result.failed("dose_partial");
        return Treatments.Result.ok(switch (d.form()) {
            case PILL -> "pill_taken";
            case INJECTION -> "drug_injected";
            case DRIP -> "drug_drip_started";
            case TOPICAL -> "drug_applied";
        });
    }

    /** Записать дозу; true — превышен предел за окно (передозировка). */
    static boolean recordDose(MedicalState m, Drug d) {
        return recordDose(m, d, 1.0);
    }

    /** Доза из шприца считается долями: 1,5 дозы — две записи, 0,5 — одна. */
    static boolean recordDose(MedicalState m, Drug d, double dose) {
        if (d.doseLimit() <= 0 || d.doseWindowSeconds() <= 0) return false;
        List<Double> list = m.doses.computeIfAbsent(d.id(), k -> new ArrayList<>());
        int units = Math.max(1, (int) Math.round(dose));
        for (int i = 0; i < units; i++) list.add(d.doseWindowSeconds());
        return list.size() > d.doseLimit();
    }

    /** Окна доз идут по времени в сети. */
    static void tickDoses(MedicalState m, double dt) {
        if (m.doses.isEmpty()) return;
        var it = m.doses.values().iterator();
        while (it.hasNext()) {
            List<Double> list = it.next();
            for (int i = list.size() - 1; i >= 0; i--) {
                double left = list.get(i) - dt;
                if (left <= 0) list.remove(i);
                else list.set(i, left);
            }
            if (list.isEmpty()) it.remove();
        }
    }
}
