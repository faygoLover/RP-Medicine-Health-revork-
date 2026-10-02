package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Tourniquet;
import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Heart;
import faygolover.rpmedicine.core.MedicalState.Pneumo;

import java.util.random.RandomGenerator;

/**
 * Лечение: проверка «лечение нужно» (предмет не тратится, игрок видит причину), выбор части тела
 * при быстром применении и сам эффект с учётом ошибки медика (п. 6 ТЗ).
 */
public final class Treatments {
    private Treatments() {}

    /** Итог применения. Ключи сообщений — {@code rpmedicine.treat.<key>}. */
    public static final class Result {
        public final boolean applied;
        public final boolean consumed;
        public final String key;
        public final double[] args;

        private Result(boolean applied, boolean consumed, String key, double... args) {
            this.applied = applied;
            this.consumed = consumed;
            this.key = key;
            this.args = args;
        }

        public static Result ok(String key, double... args) {
            return new Result(true, true, key, args);
        }

        /** Применено, но предмет не расходуется (приборы, удержание). */
        public static Result okKeep(String key, double... args) {
            return new Result(true, false, key, args);
        }

        /** Ошибка медика: предмет потрачен, эффекта нет или он слабее. */
        public static Result failed(String key, double... args) {
            return new Result(false, true, key, args);
        }
    }

    /** Что несёт предмет сверх действия: пакет крови, препарат. */
    public interface Extra {}

    /** Содержимое пакета крови для переливания: группа (null — не подписан), объём, испорчен ли. */
    public record Bag(BloodType type, double volume, boolean spoiled) implements Extra {}

    // ------------------------------------------------------------------ проверка «лечение нужно»

    /** null — лечение нужно; иначе ключ причины ({@code rpmedicine.refuse.<key>}). */
    public static String check(MedicalState m, BodyPart part, TreatmentAction a, MedicalSettings s) {
        return check(m, part, a, s, null);
    }

    /** То же с содержимым предмета (препарат). */
    public static String check(MedicalState m, BodyPart part, TreatmentAction a, MedicalSettings s, Extra extra) {
        if (a == TreatmentAction.DRUG || a == TreatmentAction.DRUG_TOPICAL)
            return extra instanceof Drug d ? Drugs.check(m, part, d, s) : "no_effect";
        BodyPartState ps = m.part(part);
        switch (a) {
            case BANDAGE, PRESSURE_DRESSING -> {
                for (Wound w : ps.wounds) if (!w.isDressed() && w.type != WoundType.BRUISE) return null;
                if (ps.fracture == BodyPartState.Fracture.OPEN && !ps.anyDressing()) return null;
                return "nothing_to_dress";
            }
            case HEMOSTATIC -> {
                if (ps.arterial) return null;
                for (Wound w : ps.wounds) if (w.dressing != Dressing.HEMOSTATIC && w.rawBleed(s) > s.moderateBleedMax) return null;
                return "no_heavy_bleeding";
            }
            case TOURNIQUET, ESMARCH -> {
                if (!part.acceptsTourniquet()) return "tourniquet_limb_only";
                if (ps.hasTourniquet()) return "tourniquet_already";
                if (limbNeedsTourniquet(m, part, s)) return null;
                return "tourniquet_not_needed";
            }
            case SPLINT -> {
                if (!part.isLimb()) return part == BodyPart.CHEST ? "splint_ribs" : "splint_limb_only";
                if (!ps.hasFracture()) return "no_fracture";
                if (ps.splint) return "splint_already";
                return null;
            }
            case OCCLUSIVE -> {
                if (part != BodyPart.CHEST) return "chest_only";
                if (ps.occlusive) return "occlusive_already";
                if (m.pneumo != Pneumo.NONE) return null;
                for (Wound w : ps.wounds) if (w.type.isPenetrating()) return null;
                return "no_chest_wound";
            }
            case NEEDLE -> {
                if (part != BodyPart.CHEST) return "chest_only";
                if (m.pneumo != Pneumo.TENSION) return "no_tension";
                return null;
            }
            case PAINKILLER -> {
                if (m.painkillerSeconds > 0) return "already_active";
                return m.rawPain >= 5 ? null : "no_pain";
            }
            case MORPHINE -> {
                return m.rawPain >= 5 || m.morphineSeconds > 0 ? null : "no_pain";
            }
            case ADRENALINE -> {
                return null;
            }
            case TXA -> {
                if (m.txaSeconds > 0) return "already_active";
                return m.totalExternalBleed(s) + m.totalInternalBleed() > 0.5 ? null : "no_bleeding";
            }
            case SURGICAL_KIT -> {
                if (!part.isTorso()) return "torso_only";
                return ps.internalBleed > 0 ? null : "no_internal";
            }
            case SALINE -> {
                if (m.salineDripRemaining > 0) return "already_dripping";
                double normal = m.normalBlood(s);
                if (m.bloodVolume >= normal * 0.97) return "volume_ok";
                if (m.saline >= normal * s.salineMaxFraction - 1) return "saline_limit";
                return null;
            }
            case AMMONIA -> {
                if (m.down == Down.FAINT) return null;
                if (m.down == Down.KNOCKDOWN) return Physiology.lifeThreat(m, s) ? "ammonia_threat" : null;
                if (m.down == Down.CLINICAL) return "ammonia_threat";
                return "not_unconscious";
            }
            case AIRWAY -> {
                if (m.down != Down.KNOCKDOWN && m.down != Down.CLINICAL) return "not_unconscious";
                return m.airway ? "airway_already" : null;
            }
            case AMBU -> {
                if (m.respiratoryArrest || m.heart != Heart.NORMAL || m.down != Down.NONE && m.spo2 < 92) return null;
                return "breathing_ok";
            }
            case DEFIBRILLATOR -> {
                if (m.heart == Heart.FIBRILLATION) return null;
                if (m.down == Down.CLINICAL && m.cprSeconds > 0) return null;
                return "no_shockable";
            }
            case CPR -> {
                return m.heart != Heart.NORMAL ? null : "pulse_present";
            }
            case PULSE_OXIMETER, TONOMETER -> {
                return null;
            }
            case BLOOD_BAG -> {
                if (m.bloodDripRemaining > 0 || m.salineDripRemaining > 0) return "already_dripping";
                if (m.bloodVolume - m.saline >= m.normalBlood(s) * 0.97) return "volume_ok";
                return null;
            }
            case BLOOD_COLLECT -> {
                if (m.bloodDripRemaining > 0) return "already_dripping";
                if (m.bloodFraction(s) < 1 - s.donationMaxLossFraction || m.isDown()) return "donor_low";
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** Нужен ли жгут: артерия или сильное кровотечение на этой конечности или дальше по ней. */
    public static boolean limbNeedsTourniquet(MedicalState m, BodyPart limb, MedicalSettings s) {
        for (BodyPartState ps : m.parts) {
            if (!ps.part.isDistalTo(limb)) continue;
            if (ps.arterial) return true;
            for (Wound w : ps.wounds) if (w.bleed(s) > s.moderateBleedMax) return true;
            if (ps.fracture == BodyPartState.Fracture.OPEN && !ps.anyDressing()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ быстрый способ: выбор части

    /** Часть, где предмет нужнее всего; null — нигде не нужен. */
    public static BodyPart bestPart(MedicalState m, TreatmentAction a, MedicalSettings s) {
        return bestPart(m, a, s, null);
    }

    public static BodyPart bestPart(MedicalState m, TreatmentAction a, MedicalSettings s, Extra extra) {
        if (a.target != TreatmentAction.Target.PART) return BodyPart.CHEST;
        if (a == TreatmentAction.DRUG_TOPICAL) return extra instanceof Drug d ? Drugs.bestPart(m, d, s) : null;
        BodyPart best = null;
        double bestScore = 0;
        for (BodyPart p : BodyPart.VALUES) {
            if (check(m, p, a, s) != null) continue;
            double score = partScore(m, m.part(p), a, s);
            if (best == null || score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private static double partScore(MedicalState m, BodyPartState ps, TreatmentAction a, MedicalSettings s) {
        double bleed = 0;
        for (Wound w : ps.wounds) if (!w.isDressed()) bleed += w.bleed(s);
        return switch (a) {
            case HEMOSTATIC -> (ps.arterial ? 1000 : 0) + bleed;
            case TOURNIQUET, ESMARCH -> {
                double sum = 0;
                for (BodyPartState o : m.parts) {
                    if (!o.part.isDistalTo(ps.part)) continue;
                    if (o.arterial) sum += 1000;
                    for (Wound w : o.wounds) sum += w.bleed(s);
                }
                yield sum;
            }
            case SPLINT -> ps.part.isLowerLimb() ? 2 : 1;
            case SURGICAL_KIT -> ps.internalBleed;
            default -> bleed + 0.01 * ps.totalSeverity();
        };
    }

    // ------------------------------------------------------------------ применение

    /**
     * Применяет действие. {@code error} — медик ошибся (решает вызывающий по шансу ошибки):
     * предмет потрачен, эффект слабее.
     */
    public static Result apply(MedicalState m, BodyPart part, TreatmentAction a, boolean error, RandomGenerator rnd, MedicalSettings s) {
        return apply(m, part, a, error, rnd, s, null);
    }

    /** {@code bag} — содержимое пакета для переливания (для остальных действий null). */
    public static Result apply(MedicalState m, BodyPart part, TreatmentAction a, boolean error, RandomGenerator rnd, MedicalSettings s, Extra extra) {
        if (a == TreatmentAction.DRUG || a == TreatmentAction.DRUG_TOPICAL)
            return extra instanceof Drug d ? Drugs.apply(m, part, d, error, rnd, s) : Result.failed("no_effect");
        Bag bag = extra instanceof Bag b ? b : null;
        BodyPartState ps = m.part(part);
        switch (a) {
            case BANDAGE -> {
                dress(ps, Dressing.BANDAGE, error, s);
                return error ? Result.failed("dressing_poor") : Result.ok("bandaged");
            }
            case PRESSURE_DRESSING -> {
                dress(ps, Dressing.PRESSURE, error, s);
                return error ? Result.failed("dressing_poor") : Result.ok("pressure_applied");
            }
            case HEMOSTATIC -> {
                dress(ps, Dressing.HEMOSTATIC, error, s);
                if (!error && ps.arterial) ps.arterial = false;
                return error ? Result.failed("dressing_poor") : Result.ok("hemostatic_applied");
            }
            case TOURNIQUET, ESMARCH -> {
                if (a == TreatmentAction.ESMARCH && rnd.nextDouble() < s.esmarchBreakChance) return Result.failed("esmarch_broke");
                if (error) return Result.failed("tourniquet_loose");
                ps.tourniquet = a == TreatmentAction.ESMARCH ? Tourniquet.ESMARCH : Tourniquet.CAT;
                ps.tourniquetSeconds = 0;
                return Result.ok("tourniquet_applied");
            }
            case SPLINT -> {
                ps.splint = true;
                ps.splintQuality = error ? 0.5 : 1.0;
                return error ? Result.failed("splint_poor") : Result.ok("splinted");
            }
            case OCCLUSIVE -> {
                if (error) return Result.failed("occlusive_leaks");
                ps.occlusive = true;
                return Result.ok("occlusive_applied");
            }
            case NEEDLE -> {
                if (error) {
                    Injuries.mergeWound(ps, WoundType.STAB, 8, s);
                    return Result.failed("needle_missed");
                }
                m.pneumo = Pneumo.OPEN;
                m.tensionProgress = 0;
                m.pneumoTimer = Physiology.lerp(s.pneumoSealMinSeconds, s.pneumoSealMaxSeconds, rnd.nextDouble());
                return Result.ok("decompressed");
            }
            case PAINKILLER -> {
                m.painkillerDelay = s.painkillerDelaySeconds;
                m.painkillerSeconds = s.painkillerMinutes * 60.0 * (error ? 0.5 : 1.0);
                return error ? Result.failed("dose_partial") : Result.ok("painkiller_taken");
            }
            case MORPHINE -> {
                boolean overdose = m.morphineSeconds > 0;
                m.morphineDelay = m.morphineSeconds > 0 ? 0 : s.morphineDelaySeconds;
                m.morphineSeconds = s.morphineMinutes * 60.0 * (error ? 0.5 : 1.0);
                if (overdose) {
                    m.morphineOverdoseSeconds = s.morphineMinutes * 30.0;
                    if (rnd.nextDouble() < s.morphineOverdoseArrestChance) m.respiratoryArrest = true;
                    return Result.ok("morphine_overdose");
                }
                return error ? Result.failed("dose_partial") : Result.ok("morphine_injected");
            }
            case ADRENALINE -> {
                boolean healthy = m.heart == Heart.NORMAL && m.down == Down.NONE && m.pressure >= 90;
                m.adrenalineInjectionSeconds = s.adrenalineInjectionSeconds * (error ? 0.5 : 1.0);
                if (healthy && rnd.nextDouble() < s.adrenalineHealthyFibrillationChance) {
                    m.heart = Heart.FIBRILLATION;
                    m.fibrillationSeconds = 0;
                    return Result.ok("adrenaline_harm");
                }
                return error ? Result.failed("dose_partial") : Result.ok("adrenaline_injected");
            }
            case TXA -> {
                m.txaSeconds = s.txaMinutes * 60.0 * (error ? 0.5 : 1.0);
                return error ? Result.failed("dose_partial") : Result.ok("txa_injected");
            }
            case SURGICAL_KIT -> {
                if (error) {
                    ps.internalBleed *= 0.5;
                    return Result.failed("surgery_partial");
                }
                ps.internalBleed = 0;
                return Result.ok("internal_stopped");
            }
            case SALINE -> {
                double vol = s.salineVolume * (error ? 0.5 : 1.0);
                m.salineDripRemaining = vol;
                m.salineDripRate = vol / Math.max(1, s.salineDripSeconds);
                return error ? Result.failed("saline_infiltrated") : Result.ok("saline_started");
            }
            case AMMONIA -> {
                if (error) return Result.failed("no_effect");
                m.painShock = false;
                m.shockAccum = 0;
                m.shockLimit = 0;
                m.concussionKoSeconds = 0;
                m.wakeSeconds = 0;
                return Result.ok("ammonia_used");
            }
            case AIRWAY -> {
                if (error) return Result.failed("airway_failed");
                m.airway = true;
                return Result.ok("airway_placed");
            }
            case AMBU -> {
                m.ambuSeconds = Math.max(m.ambuSeconds, error ? 0.8 : 1.5);
                return Result.okKeep("ventilating");
            }
            case CPR -> {
                m.cprSeconds = Math.max(m.cprSeconds, error ? 0.8 : 1.5);
                return Result.okKeep("cpr");
            }
            case DEFIBRILLATOR -> {
                if (error) return Result.failed("shock_failed");
                boolean can = m.heart == Heart.FIBRILLATION || Physiology.canRestartHeart(m, s);
                if (can && rnd.nextDouble() < s.defibSuccessChance && Physiology.canRestartHeart(m, s)) {
                    Physiology.restartHeart(m, s);
                    return Result.ok("rhythm_restored");
                }
                return Result.ok("shock_no_effect");
            }
            case PULSE_OXIMETER -> {
                boolean pulse = m.heart == Heart.NORMAL;
                return Result.okKeep("oximeter", pulse ? Math.round(m.heartRate) : 0, pulse ? Math.round(m.spo2) : 0);
            }
            case TONOMETER -> {
                if (m.heart != Heart.NORMAL || m.pressure < 20) return Result.okKeep("tonometer_none");
                double sys = Math.round(m.pressure);
                double dia = Math.round(m.pressure * 0.65);
                return Result.okKeep("tonometer", sys, dia);
            }
            case BLOOD_BAG -> {
                if (bag == null) return Result.failed("no_effect");
                // Промах мимо вены: половина пакета уходит под кожу.
                double vol = bag.volume() * (error ? 0.5 : 1.0);
                m.bloodDripRemaining = vol;
                m.bloodDripRate = vol / Math.max(1, s.transfusionSeconds);
                m.bloodDripType = bag.type();
                m.bloodDripSpoiled = bag.spoiled();
                return error ? Result.failed("transfusion_infiltrated") : Result.ok("transfusion_started");
            }
            case BLOOD_COLLECT -> {
                if (error) return Result.failed("collect_failed");
                double take = Math.min(s.bloodBagVolume, m.bloodVolume);
                double frac = m.bloodVolume > 0 ? m.saline / m.bloodVolume : 0;
                m.bloodVolume -= take;
                m.saline = Math.max(0, m.saline - take * frac);
                return Result.ok("blood_collected");
            }
            default -> { }
        }
        return Result.failed("no_effect");
    }

    private static void dress(BodyPartState ps, Dressing d, boolean error, MedicalSettings s) {
        for (Wound w : ps.wounds) {
            if (w.type == WoundType.BRUISE) continue;
            // Более сильная повязка заменяет слабую; гемостатик не снимается обычной повязкой.
            if (w.dressing.ordinal() > d.ordinal()) continue;
            w.dressing = d;
            w.dressingQuality = error ? 0.5 : 1.0;
            w.dressingAge = 0;
            w.bandageBoost = d == Dressing.BANDAGE && !error;
        }
    }

    // ------------------------------------------------------------------ снятие пустой рукой

    public enum Removal { DRESSING, TOURNIQUET, SPLINT, OCCLUSIVE }

    /** Снимает наложенное с части. Возвращает false, если снимать нечего. */
    public static boolean remove(MedicalState m, BodyPart part, Removal what) {
        BodyPartState ps = m.part(part);
        switch (what) {
            case DRESSING -> {
                boolean any = false;
                for (Wound w : ps.wounds) {
                    if (w.isDressed()) {
                        w.removeDressing();
                        any = true;
                    }
                }
                return any;
            }
            case TOURNIQUET -> {
                if (!ps.hasTourniquet()) return false;
                ps.tourniquet = Tourniquet.NONE;
                ps.tourniquetSeconds = 0;
                return true;
            }
            case SPLINT -> {
                if (!ps.splint) return false;
                ps.splint = false;
                ps.splintQuality = 1.0;
                return true;
            }
            case OCCLUSIVE -> {
                if (!ps.occlusive) return false;
                ps.occlusive = false;
                return true;
            }
        }
        return false;
    }
}
