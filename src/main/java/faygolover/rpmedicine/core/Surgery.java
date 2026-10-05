package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.SurgeryStage;
import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Pneumo;
import faygolover.rpmedicine.core.Treatments.Result;

import java.util.random.RandomGenerator;

/**
 * Пошаговая хирургия (ТЗ третьего этапа, п. 4): вскрыть скальпелем, зажать, раскрыть ретрактором,
 * специальный шаг на раскрытой части, зашить. Вскрытая часть кровит и может заразиться; без
 * обезболивания каждый шаг — сильная острая боль.
 */
public final class Surgery {
    private Surgery() {}

    /**
     * Обстановка операции, которую считает сервер: стерилен ли инструмент, множитель успеха места
     * (шанс ошибки растёт) и множитель заражения (место, нестерильный инструмент, нет маски и перчаток).
     */
    public record Context(boolean sterile, double successFactor, double infectionFactor) implements Treatments.Extra {
        public static final Context DEFAULT = new Context(true, 1.0, 1.0);
    }

    /** Действия пошаговой хирургии (проверяются и применяются здесь). */
    public static boolean isSurgical(TreatmentAction a) {
        return a == TreatmentAction.INCISE || a == TreatmentAction.CLAMP || a == TreatmentAction.RETRACT
                || a == TreatmentAction.VESSEL_SUTURE || a == TreatmentAction.OSTEOSYNTHESIS || a == TreatmentAction.DRAIN
                || a == TreatmentAction.AMPUTATE || a == TreatmentAction.ORGAN_REMOVE || a == TreatmentAction.TRANSPLANT
                || a == TreatmentAction.REATTACH;
    }

    /** Пациент не дёргается: без сознания (в том числе наркоз), зафиксирован или местная анестезия этой части. */
    public static boolean still(MedicalState m, BodyPartState ps) {
        return m.down != Down.NONE || m.restrained || ps.localAnesthesiaSeconds > 0;
    }

    /** Обезболено: без сознания или местная анестезия части. Зафиксированному больно. */
    public static boolean anesthetized(MedicalState m, BodyPartState ps) {
        return m.down != Down.NONE || ps.localAnesthesiaSeconds > 0;
    }

    /** Боль шага без обезболивания: острая боль на полминуты (может уронить болевым шоком). */
    static void stepPain(MedicalState m, BodyPartState ps, MedicalSettings s) {
        if (!anesthetized(m, ps)) m.painSpike(s.surgeryStepPain, s.surgeryStepPainSeconds);
    }

    // ------------------------------------------------------------------ проверка

    public static String check(MedicalState m, BodyPart part, TreatmentAction a, MedicalSettings s) {
        return check(m, part, a, s, null);
    }

    public static String check(MedicalState m, BodyPart part, TreatmentAction a, MedicalSettings s, Treatments.Extra extra) {
        BodyPartState ps = m.part(part);
        SurgeryStage st = ps.surgery;
        if (a == TreatmentAction.REATTACH) {
            if (!ps.missing) return "not_missing";
            if (ps.prosthesis != BodyPartState.Prosthesis.NONE) return "prosthesis_already";
            if (extra instanceof Treatments.Limb l) {
                if (l.part() != part) return "limb_wrong_part";
                if (!l.fresh()) return "limb_spoiled";
            }
            return m.down != Down.NONE || m.restrained ? null : "patient_moves";
        }
        if (ps.missing) return "part_missing";
        switch (a) {
            case INCISE -> {
                if (st != SurgeryStage.NONE) return "already_open";
                return still(m, ps) ? null : "patient_moves";
            }
            case CLAMP -> {
                if (st == SurgeryStage.NONE) return "not_open";
                return st == SurgeryStage.OPEN ? null : "already_clamped";
            }
            case RETRACT -> {
                if (st == SurgeryStage.NONE) return "not_open";
                if (st == SurgeryStage.OPEN) return "clamp_first";
                return st == SurgeryStage.RETRACTED ? "already_retracted" : null;
            }
            case VESSEL_SUTURE -> {
                if (st != SurgeryStage.RETRACTED) return "not_retracted";
                return ps.arterial ? null : "no_arterial";
            }
            case OSTEOSYNTHESIS -> {
                if (st != SurgeryStage.RETRACTED) return "not_retracted";
                if (!ps.hasFracture()) return "no_fracture";
                return ps.fixated ? "already_fixated" : null;
            }
            case AMPUTATE -> {
                if (!part.isLimb()) return "limb_only";
                return st == SurgeryStage.RETRACTED ? null : "not_retracted";
            }
            case ORGAN_REMOVE -> {
                if (!part.isTorso()) return "torso_only";
                if (st != SurgeryStage.RETRACTED) return "not_retracted";
                if (extra instanceof Treatments.OrganPick pick) return pick.organ().part == part && m.hasOrgan(pick.organ()) ? null : "no_organ";
                for (Organ o : Organ.VALUES) if (o.part == part && m.hasOrgan(o)) return null;
                return "no_organ";
            }
            case TRANSPLANT -> {
                if (!part.isTorso()) return "torso_only";
                if (st != SurgeryStage.RETRACTED) return "not_retracted";
                if (extra instanceof Treatments.DonorOrgan d) {
                    if (d.organ().part != part) return "organ_wrong_part";
                    return m.hasOrgan(d.organ()) ? "organ_present" : null;
                }
                return null;
            }
            case DRAIN -> {
                if (part != BodyPart.CHEST) return "chest_only";
                if (st != SurgeryStage.RETRACTED) return "not_retracted";
                return m.pneumo != Pneumo.NONE ? null : "no_pneumothorax";
            }
            default -> {
                return null;
            }
        }
    }

    /** Набор для швов на вскрытой части: есть что делать (кровотечение, орган или закрыть). */
    public static boolean sutureOnOpen(BodyPartState ps) {
        return ps.surgery != SurgeryStage.NONE;
    }

    // ------------------------------------------------------------------ применение

    public static Result apply(MedicalState m, BodyPart part, TreatmentAction a, boolean error, RandomGenerator rnd, MedicalSettings s,
                               Context ctx) {
        BodyPartState ps = m.part(part);
        stepPain(m, ps, s);
        // Нестерильный инструмент в открытой ране — загрязнение растёт.
        if (a != TreatmentAction.INCISE && !ctx.sterile()) ps.surgeryContamination *= s.surgeryNonSterileFactor;
        if (error) return slip(m, ps, rnd, s);
        switch (a) {
            case INCISE -> {
                ps.surgery = SurgeryStage.OPEN;
                ps.surgeryOpenSeconds = 0;
                ps.surgeryCheckSeconds = 0;
                ps.surgeryContamination = ctx.infectionFactor();
                Wound w = new Wound(WoundType.CUT, s.incisionSeverity);
                w.surgical = true;
                // Заражение разреза считается своим путём, пока часть открыта (см. tick).
                w.infectionStage = Wound.Infection.CLEAN;
                ps.wounds.add(w);
                return Result.okKeep("incised");
            }
            case CLAMP -> {
                ps.surgery = SurgeryStage.CLAMPED;
                return Result.okKeep("clamped");
            }
            case RETRACT -> {
                ps.surgery = SurgeryStage.RETRACTED;
                return Result.okKeep("retracted");
            }
            case VESSEL_SUTURE -> {
                ps.arterial = false;
                return Result.ok("artery_repaired");
            }
            case OSTEOSYNTHESIS -> {
                ps.fixated = true;
                return Result.ok("bone_fixated");
            }
            case AMPUTATE -> {
                return Limbs.amputate(m, part, s, 1.0);
            }
            case DRAIN -> {
                m.pneumo = Pneumo.NONE;
                m.pneumoTimer = 0;
                m.tensionProgress = 0;
                return Result.ok("chest_drained");
            }
            default -> {
                return Result.failed("no_effect");
            }
        }
    }

    /** Изъятие, пересадка, пришивание: содержимое предмета из {@code extra}. */
    public static Result applyTransfer(MedicalState m, BodyPart part, TreatmentAction a, boolean error, RandomGenerator rnd, MedicalSettings s,
                                       Treatments.Extra extra, double quality) {
        BodyPartState ps = m.part(part);
        stepPain(m, ps, s);
        if (error && a != TreatmentAction.REATTACH) return slip(m, ps, rnd, s);
        switch (a) {
            case ORGAN_REMOVE -> {
                Organ o = extra instanceof Treatments.OrganPick p ? p.organ() : firstOrgan(m, part);
                if (o == null) return Result.failed("no_organ");
                m.organsMissing |= o.bit();
                m.organRejection &= ~o.bit();
                return Result.ok("organ_removed");
            }
            case TRANSPLANT -> {
                if (!(extra instanceof Treatments.DonorOrgan d)) return Result.failed("no_effect");
                Organ o = d.organ();
                m.organsMissing &= ~o.bit();
                m.organs[o.ordinal()] = d.spoiled() ? 100 : Physiology.clamp(d.damage(), 0, 100);
                boolean reject = d.donor() != null && m.bloodType != null && !d.donor().canDonateTo(m.bloodType);
                if (reject) m.organRejection |= o.bit();
                else m.organRejection &= ~o.bit();
                return Result.ok(d.spoiled() ? "organ_transplanted_dead" : "organ_transplanted");
            }
            case REATTACH -> {
                ps.missing = false;
                ps.prosthesis = BodyPartState.Prosthesis.NONE;
                ps.wounds.clear();
                Wound w = new Wound(WoundType.CUT, s.reattachSeverity);
                w.surgical = true;
                w.sutured = true;
                w.sutureQuality = error ? 0.4 : 0.6 + 0.4 * quality;
                w.infectionStage = Wound.Infection.CLEAN;
                ps.wounds.add(w);
                if (part.kind == BodyPart.Kind.LEG) m.part(part.pairedLowerLimb()).missing = false;
                return error ? Result.failed("limb_reattached_poor") : Result.ok("limb_reattached");
            }
            default -> {
                return Result.failed("no_effect");
            }
        }
    }

    /** Первый орган части, который ещё на месте. */
    public static Organ firstOrgan(MedicalState m, BodyPart part) {
        for (Organ o : Organ.VALUES) if (o.part == part && m.hasOrgan(o)) return o;
        return null;
    }

    /**
     * Набор для швов на вскрытой части — по порядку: остановить внутреннее кровотечение, восстановить
     * орган (на раскрытой части), закрыть операцию. Кровотечение и орган — только на раскрытой части.
     */
    public static Result suture(MedicalState m, BodyPartState ps, boolean error, double quality, RandomGenerator rnd, MedicalSettings s) {
        stepPain(m, ps, s);
        if (ps.surgery == SurgeryStage.RETRACTED) {
            if (error) return slip(m, ps, rnd, s);
            if (ps.internalBleed > 0) {
                ps.internalBleed = 0;
                return Result.ok("internal_stopped");
            }
            Organ worst = null;
            for (Organ o : Organ.VALUES) {
                if (o.part != ps.part || m.organs[o.ordinal()] <= 0) continue;
                if (worst == null || m.organs[o.ordinal()] > m.organs[worst.ordinal()]) worst = o;
            }
            if (worst != null) {
                m.organs[worst.ordinal()] = Math.max(0, m.organs[worst.ordinal()] - s.organRepairAmount);
                return Result.ok("organ_repaired");
            }
        }
        // Закрыть: все хирургические статусы сняты, разрез и остальные раны части зашиты.
        ps.surgery = SurgeryStage.NONE;
        ps.surgeryOpenSeconds = 0;
        ps.surgeryCheckSeconds = 0;
        for (Wound w : ps.wounds) {
            if (!w.canBeSutured() || w.sutured) continue;
            w.sutured = true;
            w.sutureQuality = error ? 0.4 : 0.6 + 0.4 * quality;
            w.clot = 0;
        }
        return error ? Result.failed("surgery_closed_weak") : Result.ok("surgery_closed");
    }

    /** Пинцет на раскрытой части: все пули и осколки за раз, без риска. */
    public static Result extractAll(MedicalState m, BodyPartState ps, MedicalSettings s) {
        stepPain(m, ps, s);
        ps.bullets = 0;
        ps.fragments = 0;
        ps.foreignBodySeconds = 0;
        return Result.okKeep("foreign_all_removed");
    }

    /** Ошибка шага: порез соседних тканей, при раскрытой груди или животе — урон органу. */
    static Result slip(MedicalState m, BodyPartState ps, RandomGenerator rnd, MedicalSettings s) {
        Injuries.mergeWound(ps, WoundType.CUT, s.surgeryErrorSeverity, s);
        if (ps.surgery == SurgeryStage.RETRACTED && ps.part.isTorso()) {
            int n = 0;
            for (Organ o : Organ.VALUES) if (o.part == ps.part) n++;
            if (n > 0) {
                int pick = rnd.nextInt(n);
                for (Organ o : Organ.VALUES) {
                    if (o.part != ps.part) continue;
                    if (pick-- == 0) {
                        m.organs[o.ordinal()] = Math.min(100, m.organs[o.ordinal()] + s.surgeryErrorOrganDamage);
                        break;
                    }
                }
            }
        }
        return Result.failed("surgery_slip");
    }

    // ------------------------------------------------------------------ шаг физиологии

    /** Кровотечение вскрытой части, мл/мин: без зажима — сильное, с зажимом — слабое. */
    public static double openBleed(BodyPartState ps, MedicalSettings s) {
        return switch (ps.surgery) {
            case OPEN -> s.openPartBleed;
            case CLAMPED, RETRACTED -> s.clampedPartBleed;
            default -> 0;
        };
    }

    /**
     * Вскрытая часть раз в минуту может заразиться: шанс × загрязнение (место, инструменты,
     * экипировка) × условия (койка снижает). Заражается операционный разрез.
     */
    public static void tick(MedicalState m, StepInput in, MedicalSettings s) {
        if (!in.online) return;
        for (BodyPartState ps : m.parts) {
            if (ps.surgery == SurgeryStage.NONE) continue;
            ps.surgeryOpenSeconds += in.dt;
            ps.surgeryCheckSeconds += in.dt;
            if (ps.surgeryCheckSeconds < 60) continue;
            ps.surgeryCheckSeconds -= 60;
            if (!s.infectionEnabled) continue;
            double chance = s.openPartInfectionPerMinute * ps.surgeryContamination * in.infectionRiskFactor;
            if (in.random.nextDouble() >= chance) continue;
            for (Wound w : ps.wounds) {
                if (!w.surgical || w.isInfected()) continue;
                w.infectionStage = Wound.Infection.INFECTED;
                w.infection = 5;
                w.immuneProgress = 0;
                break;
            }
        }
    }
}
