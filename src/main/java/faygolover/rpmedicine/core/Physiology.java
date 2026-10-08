package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Fracture;
import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Heart;
import faygolover.rpmedicine.core.MedicalState.Pneumo;
import faygolover.rpmedicine.core.StepResult.Event;

import java.util.random.RandomGenerator;

/**
 * Шаг пересчёта физиологии (п. 4 ТЗ). Чистая функция над {@link MedicalState}: без Minecraft,
 * поэтому покрывается юнит-тестами. Вызывается раз в {@code stepTicks} тиков; у здорового
 * игрока ({@link MedicalState#isQuiet}) не вызывается вовсе.
 */
public final class Physiology {
    private Physiology() {}

    public static StepResult step(MedicalState m, StepInput in, MedicalSettings s) {
        StepResult r = new StepResult();
        double dt = in.dt;
        RandomGenerator rnd = in.random;

        tickTimers(m, in, s);
        tickLimbs(m, in, s, r);
        double bleedPerMin = tickBlood(m, in, s, r);
        double hungerFactor = in.satiety < s.hungerThreshold ? s.hungerHealFactor : 1.0;
        if (in.online) Healing.advance(m, dt * in.healFactor * hungerFactor, s);
        Infections.tick(m, in, s, r);
        Surgery.tick(m, in, s);
        Limbs.tickNecrosis(m, in, s);
        Metabolism.tick(m, in, s);
        Substances.tick(m, in, s);
        Nutrition.tick(m, in, s);
        Organs.tick(m, in, s);
        tickPain(m, in, s, r);
        tickChest(m, dt, s, r);
        tickCirculation(m, in, s, r, bleedPerMin);
        tickBreathing(m, in, s);
        tickBrainAndConsciousness(m, in, s, r);
        return r;
    }

    // ------------------------------------------------------------------ таймеры

    private static void tickTimers(MedicalState m, StepInput in, MedicalSettings s) {
        double dt = in.dt;
        m.adrenalineSeconds = dec(m.adrenalineSeconds, dt);
        m.stabilizedSeconds = dec(m.stabilizedSeconds, dt);
        if (m.down == Down.NONE) m.stabilizationUsed = false;
        m.adrenalineInjectionSeconds = dec(m.adrenalineInjectionSeconds, dt);
        if (m.painkillerDelay > 0) m.painkillerDelay = dec(m.painkillerDelay, dt);
        else m.painkillerSeconds = dec(m.painkillerSeconds, dt);
        if (m.morphineDelay > 0) m.morphineDelay = dec(m.morphineDelay, dt);
        else m.morphineSeconds = dec(m.morphineSeconds, dt);
        m.morphineOverdoseSeconds = dec(m.morphineOverdoseSeconds, dt);
        m.txaSeconds = dec(m.txaSeconds, dt);
        m.ambuSeconds = dec(m.ambuSeconds, dt);
        m.cprSeconds = dec(m.cprSeconds, dt);
        m.cprRecentSeconds = dec(m.cprRecentSeconds, dt);
        m.healBoostSeconds = dec(m.healBoostSeconds, dt);
        m.concussionKoSeconds = dec(m.concussionKoSeconds, dt);
        m.concussion = Math.max(0, m.concussion - s.concussionDecayPerSecond * (1 + m.effect(DrugEffect.CONCUSSION_RELIEF)) * dt);
        tickDrugTimers(m, dt, in.online, s, in.random);
        m.acutePainSeconds = dec(m.acutePainSeconds, dt);
        if (m.acutePainSeconds <= 0) m.acutePain = 0;
        m.nauseaSeconds = dec(m.nauseaSeconds, dt);
        if (m.nauseaSeconds <= 0) m.vomitTimer = 0;
        m.deafSeconds = dec(m.deafSeconds, dt);
        if (in.online) m.postClinicalSeconds = dec(m.postClinicalSeconds, dt);
        if (m.cprSeconds <= 0) m.cprAccum = 0;
        // Воздуховод выпадает, когда человек приходит в себя; трубку в сознании не терпят — вынимают.
        if (m.down == Down.NONE) {
            m.airway = false;
            m.intubated = false;
        }
        for (BodyPartState ps : m.parts) if (ps.localAnesthesiaSeconds > 0) ps.localAnesthesiaSeconds = dec(ps.localAnesthesiaSeconds, dt);
        if (m.morphineOverdoseSeconds <= 0 && m.respiratoryArrest && m.heart == Heart.NORMAL && m.organ(Organ.LUNGS) < 100)
            m.respiratoryArrest = false;
        // Лёгкие 100 % — дыхания нет (третий этап).
        if (m.organ(Organ.LUNGS) >= 100) m.respiratoryArrest = true;
    }

    /** Таймеры лекарств второго этапа: эффекты (сначала задержка, потом действие), опиаты, окна доз (в сети). */
    static void tickDrugTimers(MedicalState m, double dt, boolean online, MedicalSettings s, RandomGenerator rnd) {
        m.opioidSeconds = dec(m.opioidSeconds, dt);
        DrugLevels.tick(m, dt, s, rnd);
        if (online) Drugs.tickDoses(m, dt);
        var it = m.effects.values().iterator();
        while (it.hasNext()) {
            DrugEffect.Active a = it.next();
            if (a.delay > 0) a.delay = dec(a.delay, dt);
            else a.seconds = dec(a.seconds, dt);
            if (a.seconds <= 0 && a.delay <= 0) it.remove();
        }
    }

    private static double dec(double v, double dt) {
        return v > 0 ? Math.max(0, v - dt) : 0;
    }

    // ------------------------------------------------------------------ конечности

    private static void tickLimbs(MedicalState m, StepInput in, MedicalSettings s, StepResult r) {
        double dt = in.dt;
        RandomGenerator rnd = in.random;
        for (BodyPartState ps : m.parts) {
            // Жгут: безопасное время, потом ишемия.
            if (ps.hasTourniquet()) {
                ps.tourniquetSeconds += dt;
                double over = ps.tourniquetSeconds - s.tourniquetSafeMinutes * 60.0;
                if (over > 0) ps.ischemia = Math.min(100, ps.ischemia + s.tourniquetIschemiaPerMinute * dt / 60.0);
            } else if (ps.ischemia > 0 && in.online) {
                ps.ischemia = Math.max(0, ps.ischemia - s.ischemiaRecoveryPerMinute * dt / 60.0);
            }

            for (Wound w : ps.wounds) {
                if (w.isDressed()) w.dressingAge += dt;
                if (w.rawBleed(s) <= s.slightBleedMax && w.rawBleed(s) > 0 && w.clot < 1)
                    w.clot = Math.min(1, w.clot + dt / (s.clotMinutes * 60.0));
            }

            // Свежая повязка может открыться от бега и прыжков.
            if (in.sprintSeconds > 0 || in.jumps > 0) {
                double pKeep = Math.pow(1 - s.reopenChancePerSprintSecond, in.sprintSeconds)
                        * Math.pow(1 - s.reopenChancePerJump, in.jumps);
                for (Wound w : ps.wounds) {
                    if (w.isFreshDressing(s) && rnd.nextDouble() > pKeep) {
                        w.removeDressing();
                        r.add(Event.DRESSING_REOPENED);
                    }
                }
            }

            // Движение на сломанной ноге без шины усугубляет травму.
            if (s.fracturesEnabled && in.distance > 0 && ps.part.isLowerLimb() && ps.hasFracture() && !ps.splint) {
                double sev = s.brokenLegWalkDamagePerBlock * in.distance;
                Injuries.mergeWound(ps, WoundType.BRUISE, sev, s);
                if (ps.fracture == Fracture.CLOSED && rnd.nextDouble() < 1 - Math.pow(1 - s.brokenLegOpenChancePerBlock, in.distance)) {
                    ps.fracture = Fracture.OPEN;
                    r.add(Event.FRACTURE_WORSENED);
                }
            }
        }
    }

    // ------------------------------------------------------------------ кровь

    /** Наружное кровотечение части с учётом жгута, мл/мин (без множителей перфузии и перков). */
    public static double partExternalBleed(MedicalState m, BodyPartState ps, MedicalSettings s) {
        if (isUnderTourniquet(m, ps.part)) return 0;
        double sum = 0;
        for (Wound w : ps.wounds) sum += w.bleed(s);
        if (ps.arterial) sum += s.arterialBleedRate;
        sum += Surgery.openBleed(ps, s);
        if (ps.fracture == Fracture.OPEN) {
            boolean dressed = ps.anyDressing();
            sum += dressed ? s.openFractureBleed * 0.2 : s.openFractureBleed;
        }
        return sum;
    }

    public static boolean isUnderTourniquet(MedicalState m, BodyPart part) {
        for (BodyPartState t : m.parts) {
            if (t.hasTourniquet() && part.isDistalTo(t.part)) return true;
        }
        return false;
    }

    /**
     * Устранить угрозу жизни и вернуть сознание: сердце, дыхание, напряжённый пневмоторакс, кровь не ниже
     * доли {@code bloodFraction}. Общее для «поднять» ГМа и тотема бессмертия (второй этап, п. 13).
     * Возвращает true, если человек лежал.
     */
    public static boolean rescue(MedicalState m, MedicalSettings s, double bloodFraction) {
        boolean wasDown = m.isDown();
        m.heart = MedicalState.Heart.NORMAL;
        m.respiratoryArrest = false;
        m.morphineOverdoseSeconds = 0;
        if (m.pneumo == MedicalState.Pneumo.TENSION) {
            m.pneumo = MedicalState.Pneumo.OPEN;
            m.tensionProgress = 0;
            m.pneumoTimer = s.pneumoSealMaxSeconds;
        }
        m.bloodVolume = Math.max(m.bloodVolume, m.normalBlood(s) * bloodFraction);
        m.spo2 = Math.max(m.spo2, 92);
        m.pressure = Math.max(m.pressure, pressureFromVolume(m.bloodFraction(s), s));
        m.heartRate = Math.max(m.heartRate, s.normalHeartRate);
        m.brain = Math.max(m.brain, 30);
        m.painShock = false;
        m.shockAccum = 0;
        m.concussionKoSeconds = 0;
        m.sepsis = Math.min(m.sepsis, 99);
        m.consciousness = 100;
        m.down = MedicalState.Down.NONE;
        m.wakeSeconds = -1;
        m.knockdownNoTimer = false;
        return wasDown;
    }

    /** Обезвоживание 0–1: насколько вода ниже порога. */
    public static double dehydration(StepInput in, MedicalSettings s) {
        if (in.hydration >= s.dehydrationThreshold || s.dehydrationThreshold <= 0) return 0;
        return clamp((s.dehydrationThreshold - in.hydration) / s.dehydrationThreshold, 0, 1);
    }

    /** Множитель кровотечения от давления: при низком давлении кровь уходит медленнее. */
    static double perfusionBleedFactor(MedicalState m, MedicalSettings s) {
        if (m.heart != Heart.NORMAL) return m.cprSeconds > 0 ? 0.3 : 0.1;
        return clamp(0.4 + 0.6 * m.pressure / s.normalPressure, 0.15, 1.2);
    }

    private static double tickBlood(MedicalState m, StepInput in, MedicalSettings s, StepResult r) {
        double dt = in.dt;
        double external = 0;
        double internal = 0;
        for (BodyPartState ps : m.parts) {
            external += partExternalBleed(m, ps, s);
            internal += ps.internalBleed;
        }
        if (m.txaSeconds > 0) {
            external *= s.txaExternalFactor;
            internal *= s.txaInternalFactor;
        }
        // Свёртывание: положительное — меньше кровит, отрицательное (передозировка НПВС) — больше.
        double coag = clamp(m.effect(DrugEffect.COAGULATION), -1, 0.9);
        external *= 1 - coag;
        internal *= 1 - coag;
        // Печень: кровь сворачивается хуже (третий этап).
        double liver = Organs.liverBleedFactor(m, s);
        external *= liver;
        internal *= liver;
        double perMin = (external + internal) * s.bleedMultiplier * in.traits.bleedFactor(s) * perfusionBleedFactor(m, s);
        double lost = perMin * dt / 60.0;
        if (lost > 0) {
            // Физраствор уходит вместе с кровью пропорционально.
            double frac = m.bloodVolume > 0 ? m.saline / m.bloodVolume : 0;
            m.bloodVolume = Math.max(0, m.bloodVolume - lost);
            m.saline = Math.max(0, m.saline - lost * frac);
        }

        // Внутреннее кровотечение спадает само, медленно (быстрее под транексамовой кислотой).
        double decay = s.internalBleedDecayPerMinute * (m.txaSeconds > 0 ? 3 : 1) * dt / 60.0;
        for (BodyPartState ps : m.parts) {
            if (ps.internalBleed > 0) {
                ps.internalBleed = ps.internalBleed * (1 - decay);
                if (ps.internalBleed < 1) ps.internalBleed = 0;
            }
        }

        double normal = m.normalBlood(s);
        // Капельница: только пока пациент неподвижен.
        if (m.salineDripRemaining > 0 && in.still) {
            double add = Math.min(m.salineDripRemaining, m.salineDripRate * in.dripFactor * dt);
            m.salineDripRemaining -= add;
            infuseSaline(m, add, s);
        }
        // Переливание крови: настоящая кровь, несёт кислород.
        if (m.bloodDripRemaining > 0 && in.still) {
            // У лежачего в шоке кровь льют струйно — быстрее (замечание 19).
            double fast = m.down != Down.NONE ? s.dripDownedFactor : 1.0;
            double add = Math.min(m.bloodDripRemaining, m.bloodDripRate * in.dripFactor * fast * dt);
            m.bloodDripRemaining -= add;
            transfuse(m, add, in, s, r);
        }
        if (m.transfusionReactionSeconds > 0 && !(m.bloodDripRemaining > 0 && incompatibleDrip(m)))
            m.transfusionReactionSeconds = dec(m.transfusionReactionSeconds, dt);
        // Физраствор сам уходит из сосудов.
        if (m.saline > 0) {
            double out = Math.min(m.saline, m.saline * s.salineLossPerHour * dt / 3600.0);
            m.saline -= out;
            m.bloodVolume -= out;
        }
        // Восстановление крови: только в сети и без кровотечения.
        if (in.online && perMin < 0.01) regenerateBlood(m, s.bloodRegenPerHour * in.bloodRegenFactor * dt / 3600.0, s);
        m.bloodVolume = clamp(m.bloodVolume, 0, normal);
        return perMin;
    }

    /** Добавляет физраствор с учётом предела замены. Возвращает фактически влитый объём. */
    public static double infuseSaline(MedicalState m, double ml, MedicalSettings s) {
        double normal = m.normalBlood(s);
        double room = Math.max(0, normal - m.bloodVolume);
        double salineRoom = Math.max(0, normal * s.salineMaxFraction - m.saline);
        double add = Math.min(ml, Math.min(room, salineRoom));
        m.bloodVolume += add;
        m.saline += add;
        return add;
    }

    static boolean incompatibleDrip(MedicalState m) {
        return m.bloodDripType != null && m.bloodType != null && !m.bloodDripType.canDonateTo(m.bloodType);
    }

    /** Влить {@code ml} крови из пакета: несовместимая — реакция, испорченная — сепсис. */
    static void transfuse(MedicalState m, double ml, StepInput in, MedicalSettings s, StepResult r) {
        if (m.bloodDripSpoiled) {
            Infections.spoiledBlood(m, s);
            m.bloodDripSpoiled = false;
        }
        if (incompatibleDrip(m)) {
            if (m.transfusionReactionSeconds <= 0) {
                r.add(Event.TRANSFUSION_REACTION);
                if (in.random.nextDouble() < s.transfusionReactionArrestChance && m.heart == Heart.NORMAL) {
                    m.heart = Heart.FIBRILLATION;
                    m.fibrillationSeconds = 0;
                    r.add(Event.HEART_FIBRILLATION);
                }
            }
            // Пока несовместимая кровь капает, реакция не проходит.
            m.transfusionReactionSeconds = Math.max(m.transfusionReactionSeconds,
                    lerp(s.transfusionReactionMinMinutes, s.transfusionReactionMaxMinutes, in.random.nextDouble()) * 60.0);
        }
        regenerateBlood(m, ml, s);
    }

    /** Восстановление настоящей крови: сначала объём, затем замещение физраствора. */
    public static void regenerateBlood(MedicalState m, double ml, MedicalSettings s) {
        double normal = m.normalBlood(s);
        double toVolume = Math.min(ml, Math.max(0, normal - m.bloodVolume));
        m.bloodVolume += toVolume;
        double rest = ml - toVolume;
        if (rest > 0 && m.saline > 0) m.saline = Math.max(0, m.saline - rest);
    }

    // ------------------------------------------------------------------ боль

    /** Боль части без обезболивания (сумма источников части не складывается — берётся сильнейший плюс доля). */
    public static double partPain(MedicalState m, BodyPartState ps, MedicalSettings s) {
        double max = 0;
        double sum = 0;
        for (Wound w : ps.wounds) {
            double p = w.pain(s);
            sum += p;
            max = Math.max(max, p);
        }
        double fp = fracturePain(ps, s);
        sum += fp;
        max = Math.max(max, fp);
        double ip = ps.ischemia * 0.6 + (ps.hasTourniquet() ? 10 : 0);
        sum += ip;
        max = Math.max(max, ip);
        if (ps.dislocated) {
            double dp = s.dislocationPain * (ps.splint ? s.splintPainFactor : 1.0);
            sum += dp;
            max = Math.max(max, dp);
        }
        double np = Limbs.necrosisPain(ps);
        sum += np;
        max = Math.max(max, np);
        double op = Organs.pain(m, ps.part, s);
        sum += op;
        max = Math.max(max, op);
        double total = Math.min(100, max + (sum - max) * s.otherPainFactor);
        // Местная анестезия (третий этап): боль части почти не чувствуется.
        return ps.localAnesthesiaSeconds > 0 ? total * s.localAnesthesiaPainFactor : total;
    }

    static double fracturePain(BodyPartState ps, MedicalSettings s) {
        if (!ps.hasFracture()) return 0;
        double p;
        if (ps.part == BodyPart.CHEST) p = s.ribFracturePain;
        else p = ps.fracture == Fracture.OPEN ? s.fracturePainOpen : s.fracturePainClosed;
        if (ps.splint) p *= s.splintPainFactor + (1 - s.splintPainFactor) * (1 - ps.splintQuality) * 0.5;
        return p;
    }

    public static double analgesia(MedicalState m, MedicalSettings s) {
        double a = 0;
        if (m.painkillerSeconds > 0 && m.painkillerDelay <= 0) a += s.painkillerStrength;
        if (m.morphineSeconds > 0 && m.morphineDelay <= 0) a += s.morphineStrength;
        a += m.effect(DrugEffect.ANALGESIA);
        return a;
    }

    public static boolean adrenalineActive(MedicalState m) {
        return m.adrenalineSeconds > 0 || m.adrenalineInjectionSeconds > 0;
    }

    private static void tickPain(MedicalState m, StepInput in, MedicalSettings s, StepResult r) {
        double max = 0;
        double sum = 0;
        for (BodyPartState ps : m.parts) {
            double p = partPain(m, ps, s);
            sum += p;
            max = Math.max(max, p);
        }
        if (m.acutePainSeconds > 0) {
            sum += m.acutePain;
            max = Math.max(max, m.acutePain);
        }
        if (m.transfusionReactionSeconds > 0) {
            // Реакция на несовместимую кровь: боль в спине и груди.
            sum += s.transfusionReactionPain;
            max = Math.max(max, s.transfusionReactionPain);
        }
        double raw = Math.min(100, (max + (sum - max) * s.otherPainFactor) * s.painMultiplier);
        m.rawPain = raw;
        double suppress = analgesia(m, s) + (adrenalineActive(m) ? s.adrenalinePainSuppression : 0);
        m.pain = clamp(raw - suppress, 0, 100);
        // Под наркозом боль не чувствуется, болевой шок не копится (третий этап).
        if (m.effect(DrugEffect.ANESTHESIA) > 0) m.pain = 0;

        // Болевой шок копится при боли выше порога.
        double threshold = in.traits.shockThreshold(s);
        if (m.pain >= threshold) {
            if (m.shockLimit <= 0) m.shockLimit = lerp(s.painShockMinSeconds, s.painShockMaxSeconds, in.random.nextDouble());
            // Седация замедляет болевой шок.
            m.shockAccum += in.dt * (m.effect(DrugEffect.SEDATION) > 0 ? 0.5 : 1.0);
            if (!m.painShock && m.shockAccum >= m.shockLimit) {
                m.painShock = true;
                r.add(Event.PAIN_SHOCK);
            }
        } else {
            m.shockAccum = Math.max(0, m.shockAccum - in.dt * 0.5);
            if (m.shockAccum <= 0) m.shockLimit = 0;
            if (m.painShock && m.pain < threshold - s.painShockRecoveryMargin) {
                m.painShock = false;
                m.shockAccum = 0;
                m.shockLimit = 0;
            }
        }
    }

    // ------------------------------------------------------------------ грудь

    private static void tickChest(MedicalState m, double dt, MedicalSettings s, StepResult r) {
        if (m.pneumo == Pneumo.OPEN) {
            if (!m.part(BodyPart.CHEST).occlusive) {
                m.pneumoTimer -= dt;
                if (m.pneumoTimer <= 0) {
                    m.pneumo = Pneumo.TENSION;
                    m.tensionProgress = 0;
                    r.add(Event.TENSION_PNEUMOTHORAX);
                }
            }
        } else if (m.pneumo == Pneumo.TENSION) {
            m.tensionProgress = Math.min(1, m.tensionProgress + dt / s.tensionArrestSeconds);
        }
    }

    // ------------------------------------------------------------------ давление, пульс, сердце

    /** Давление от доли объёма крови (без поправок): кусочно-линейная кривая по п. 4.1–4.2 ТЗ. */
    public static double pressureFromVolume(double f, MedicalSettings s) {
        double n = s.normalPressure;
        if (f >= 0.85) return n;
        if (f >= 0.70) return lerp(0.75 * n, n, (f - 0.70) / 0.15);
        if (f >= 0.60) return lerp(0.5 * n, 0.75 * n, (f - 0.60) / 0.10);
        if (f >= 0.50) return lerp(n / 3.0, 0.5 * n, (f - 0.50) / 0.10);
        if (f >= 0.30) return lerp(0, n / 3.0, (f - 0.30) / 0.20);
        return 0;
    }

    private static void tickCirculation(MedicalState m, StepInput in, MedicalSettings s, StepResult r, double bleedPerMin) {
        double dt = in.dt;
        RandomGenerator rnd = in.random;
        double f = m.bloodFraction(s);
        // Обезвоживание: для давления крови меньше, до −10 % (второй этап, п. 12).
        double fPressure = f - s.dehydrationVolumeLoss * dehydration(in, s);
        boolean tensionArrest = m.pneumo == Pneumo.TENSION && m.tensionProgress >= 1.0;

        // Переходы состояния сердца.
        if (m.heart != Heart.ARREST && (f <= 1 - s.arrestBloodLossFraction || tensionArrest || m.organ(Organ.HEART) >= 100)) {
            m.heart = Heart.ARREST;
            m.fibrillationSeconds = 0;
            r.add(Event.HEART_ARREST);
        }
        if (m.heart == Heart.NORMAL) {
            if (m.pressure < s.fibrillationPressure) m.lowPressureSeconds += dt;
            else m.lowPressureSeconds = 0;
            if (m.spo2 < 40) m.hypoxiaSeconds += dt;
            else m.hypoxiaSeconds = 0;
            boolean septicArrhythmia = m.sepsis >= 60 && rnd.nextDouble() < s.sepsisFibrillationPerHour * dt / 3600.0;
            // Повреждённое сердце (≥ 50) под нагрузкой срывается в фибрилляцию (третий этап).
            boolean cardiac = m.organ(Organ.HEART) >= 50 && (m.heartRate > 130 || f < 0.7)
                    && rnd.nextDouble() < s.heartFibrillationPerHour * dt / 3600.0;
            if (m.lowPressureSeconds >= s.fibrillationDelaySeconds || m.hypoxiaSeconds >= 30 || septicArrhythmia || cardiac) {
                m.heart = Heart.FIBRILLATION;
                m.fibrillationSeconds = 0;
                m.lowPressureSeconds = 0;
                m.hypoxiaSeconds = 0;
                r.add(Event.HEART_FIBRILLATION);
            }
        } else if (m.heart == Heart.FIBRILLATION) {
            m.fibrillationSeconds += dt;
            if (m.fibrillationSeconds >= s.fibrillationToArrestSeconds) {
                m.heart = Heart.ARREST;
                r.add(Event.HEART_ARREST);
            }
        }

        // Остановка: СЛР вместе с адреналином даёт шанс запуска (если причины устранены).
        if (m.heart == Heart.ARREST && m.cprSeconds > 0 && m.adrenalineInjectionSeconds > 0 && canRestartHeart(m, s)) {
            m.cprAccum += dt;
            while (m.cprAccum >= 5.0) {
                m.cprAccum -= 5.0;
                if (rnd.nextDouble() < s.cprRestartChance) {
                    restartHeart(m, s);
                    r.add(Event.HEART_RESTARTED);
                    break;
                }
            }
        }

        double target;
        if (m.heart == Heart.NORMAL) {
            target = pressureFromVolume(fPressure, s) - Organs.heartPressurePenalty(m, s);
            if (m.adrenalineSeconds > 0) target += 10;
            if (m.adrenalineInjectionSeconds > 0) target += s.adrenalineInjectionPressure;
            if (m.pain >= 60) target += 8;
            if (m.morphineSeconds > 0 && m.morphineDelay <= 0) target -= 5;
            if (m.pneumo == Pneumo.TENSION) target -= s.tensionPressureDrop * m.tensionProgress;
            target += m.effect(DrugEffect.PRESSURE);
            if (m.sepsis >= 60) target -= s.sepsisPressureDrop60;
            else if (m.sepsis >= 30) target -= s.sepsisPressureDrop30;
            if (m.transfusionReactionSeconds > 0) target -= s.transfusionReactionPressureDrop;
            target = clamp(target, 0, 200);
        } else {
            target = m.cprSeconds > 0 ? 25 : 0;
        }
        m.pressure += (target - m.pressure) * Math.min(1.0, dt / 4.0);
        if (Math.abs(m.pressure - target) < 0.05) m.pressure = target;

        if (m.heart == Heart.NORMAL) {
            double hr = s.normalHeartRate;
            if (m.pressure < s.normalPressure) hr += Math.min(100, (s.normalPressure - m.pressure) * 1.2);
            hr += m.pain * 0.3;
            if (m.adrenalineSeconds > 0) hr += 20;
            if (m.adrenalineInjectionSeconds > 0) hr += 40;
            if (m.morphineSeconds > 0 && m.morphineDelay <= 0) hr -= 8;
            if (m.spo2 < 90) hr += (90 - m.spo2) * 0.8;
            if (m.bodyTemp > 37) hr += (m.bodyTemp - 37) * s.feverHeartRatePerDegree;
            // Переохлаждение: пульс реже.
            if (m.bodyTemp < 35) hr -= (35 - m.bodyTemp) * 10;
            hr += m.effect(DrugEffect.HEART_RATE);
            hr -= m.effect(DrugEffect.SEDATION) * 0.2;
            hr = clamp(hr, 35, 200);
            m.heartRate += (hr - m.heartRate) * Math.min(1.0, dt / 3.0);
            if (Math.abs(m.heartRate - hr) < 0.05) m.heartRate = hr;
        } else {
            m.heartRate = 0;
        }
    }

    /** Можно ли запустить сердце: объём крови, напряжённый пневмоторакс, массивное кровотечение. */
    /**
     * Что мешает запустить сердце (ключ для подсказки медику) или null. Причины, как в {@link #canRestartHeart}.
     */
    public static String restartBlocker(MedicalState m, MedicalSettings s) {
        if (m.bloodFraction(s) <= 1 - s.arrestBloodLossFraction + 0.05) return "low_blood";
        if (m.pneumo == Pneumo.TENSION) return "tension_pneumo";
        if (m.organ(Organ.HEART) >= 100) return "heart_destroyed";
        for (BodyPartState ps : m.parts) {
            if (ps.arterial && !isUnderTourniquet(m, ps.part)) return "arterial";
        }
        return null;
    }

    public static boolean canRestartHeart(MedicalState m, MedicalSettings s) {
        if (m.bloodFraction(s) <= 1 - s.arrestBloodLossFraction + 0.05) return false;
        if (m.pneumo == Pneumo.TENSION) return false;
        if (m.organ(Organ.HEART) >= 100) return false;
        for (BodyPartState ps : m.parts) {
            if (ps.arterial && !isUnderTourniquet(m, ps.part)) return false;
        }
        return true;
    }

    public static void restartHeart(MedicalState m, MedicalSettings s) {
        m.heart = Heart.NORMAL;
        m.fibrillationSeconds = 0;
        m.lowPressureSeconds = 0;
        m.hypoxiaSeconds = 0;
        m.cprAccum = 0;
        // Сердце начинает с низкого давления, дальше выходит на свою кривую.
        m.pressure = Math.max(m.pressure, 50);
        m.heartRate = Math.max(m.heartRate, 110);
        if (m.morphineOverdoseSeconds <= 0 && m.organ(Organ.LUNGS) < 100) m.respiratoryArrest = false;
    }

    // ------------------------------------------------------------------ дыхание

    /** Эффективность вентиляции 0–1. */
    public static double ventilation(MedicalState m, StepInput in) {
        if (in.suffocating) return 0;
        double v;
        // Интубированного на операционном столе дышит аппарат (третий этап).
        boolean machine = m.intubated && in.ventilator;
        if (m.respiratoryArrest || m.heart != Heart.NORMAL) {
            v = machine ? 1.0 : m.ambuSeconds > 0 ? (m.intubated ? 1.0 : 0.9) : 0;
        } else if (machine) {
            v = 1;
        } else {
            v = 1;
            // Без сознания (не в обмороке) западает язык: воздуховод или трубка держат дыхание.
            if ((m.down == Down.KNOCKDOWN || m.down == Down.CLINICAL) && !m.airway && !m.intubated && m.ambuSeconds <= 0) v = 0.6;
            if (m.morphineOverdoseSeconds > 0 && m.ambuSeconds <= 0) v *= 0.6;
            if (m.ambuSeconds <= 0) v *= 1 - respiratoryDepression(m);
        }
        return v;
    }

    /** Угнетение дыхания лекарствами 0–0,9: эффект и седация вместе с опиатами. */
    public static double respiratoryDepression(MedicalState m) {
        double d = m.effect(DrugEffect.RESP_DEPRESSION);
        boolean opioid = (m.morphineSeconds > 0 && m.morphineDelay <= 0) || m.opioidSeconds > 0;
        if (m.effect(DrugEffect.SEDATION) > 0 && opioid) d += 0.3;
        return clamp(d, 0, 0.9);
    }

    public static double spo2Ceiling(MedicalState m, MedicalSettings s) {
        double c = s.spo2Normal;
        boolean sealed = m.part(BodyPart.CHEST).occlusive;
        switch (m.pneumo) {
            case OPEN -> c -= sealed ? s.sealedPneumoSpo2Penalty : s.openPneumoSpo2Penalty;
            case TENSION -> c -= s.tensionPneumoSpo2Penalty * (0.5 + 0.5 * m.tensionProgress);
            default -> { }
        }
        if (m.part(BodyPart.CHEST).hasFracture()) c -= s.ribSpo2Penalty;
        double cap = m.oxygenCapacity(s);
        if (cap < 0.6) c -= (0.6 - cap) * 80;
        if (m.sepsis >= 60) c -= s.sepsisSpo2Penalty;
        c -= Organs.lungsSpo2Penalty(m, s);
        return clamp(c, 0, 100);
    }

    private static void tickBreathing(MedicalState m, StepInput in, MedicalSettings s) {
        double dt = in.dt;
        double v = ventilation(m, in);
        boolean circulating = m.heart == Heart.NORMAL;
        double ceiling = spo2Ceiling(m, s);
        // Кислород возвращает часть потерянного потолка (второй этап, п. 2.1).
        if (in.oxygen && ceiling < s.spo2Normal) ceiling += (s.spo2Normal - ceiling) * clamp(s.oxygenTherapyFactor, 0, 1);
        // Мешок Амбу — принудительные вдохи: тоже поднимает потолок, хоть и меньше кислорода (замечание 69).
        if (m.ambuSeconds > 0 && ceiling < s.spo2Normal) ceiling += (s.spo2Normal - ceiling) * clamp(s.ambuCeilingFactor, 0, 1);
        double target;
        double fall;
        if (v <= 0.01) {
            target = 0;
            fall = s.spo2FallPerSecondApnea * (m.cprSeconds > 0 ? 0.5 : 1);
        } else if (!circulating) {
            // Сердце стоит: без СЛР кислород не разносится.
            target = m.cprSeconds > 0 ? Math.min(ceiling, 80) * v : 0;
            fall = s.spo2FallPerSecondApnea * (m.cprSeconds > 0 ? 0.5 : 1);
        } else {
            target = ceiling - (1 - v) * 50;
            fall = v < 1 ? s.spo2FallPerSecondObstructed + (1 - v) : s.spo2RisePerSecond;
        }
        if (m.spo2 < target) m.spo2 = Math.min(target, m.spo2 + s.spo2RisePerSecond * dt);
        else m.spo2 = Math.max(target, m.spo2 - fall * dt);
        m.spo2 = clamp(m.spo2, 0, 100);

        if (m.respiratoryArrest || m.heart != Heart.NORMAL || in.suffocating) {
            m.respRate = m.ambuSeconds > 0 ? 12 : 0;
            return;
        }
        double rr = s.normalRespRate + m.pain * 0.08;
        BodyPartState chest = m.part(BodyPart.CHEST);
        rr += (100 - chest.integrity()) * 0.05;
        if (m.pneumo == Pneumo.OPEN) rr += chest.occlusive ? 3 : 6;
        if (m.pneumo == Pneumo.TENSION) rr += 12;
        if (chest.hasFracture()) rr += 3;
        if (m.spo2 < 94) rr += (94 - m.spo2) * 0.5;
        if (m.pressure < 90) rr += (90 - m.pressure) * 0.1;
        if (m.morphineSeconds > 0 && m.morphineDelay <= 0) rr -= 4;
        if (m.morphineOverdoseSeconds > 0) rr -= 6;
        rr -= respiratoryDepression(m) * 10;
        if (m.bodyTemp > 37.5) rr += (m.bodyTemp - 37.5) * 2;
        if (m.down != Down.NONE) rr -= 2;
        rr = clamp(rr, 4, 45);
        m.respRate += (rr - m.respRate) * Math.min(1.0, dt / 3.0);
        if (Math.abs(m.respRate - rr) < 0.05) m.respRate = rr;
    }

    // ------------------------------------------------------------------ мозг и сознание

    /** Доставка кислорода к мозгу 0–1. */
    public static double oxygenDelivery(MedicalState m, MedicalSettings s) {
        double perfusion;
        if (m.heart == Heart.NORMAL) perfusion = clamp((m.pressure - 30) / 50.0, 0, 1);
        else perfusion = m.cprSeconds > 0 ? s.cprPerfusion : 0;
        double oxygen = clamp((m.spo2 - 50) / 35.0, 0, 1);
        return perfusion * oxygen;
    }

    /** Есть ли угроза жизни (нокдаун, а не обморок; таймер-мозг тает). */
    public static boolean lifeThreat(MedicalState m, MedicalSettings s) {
        if (m.heart != Heart.NORMAL) return true;
        if (m.respiratoryArrest && m.ambuSeconds <= 0) return true;
        if (m.bloodFraction(s) < 0.6) return true;
        if (m.spo2 < 60 || m.pressure < 60) return true;
        if (m.pneumo == Pneumo.TENSION) return true;
        if (m.sepsis >= 100) return true;
        for (BodyPart p : new BodyPart[]{BodyPart.HEAD, BodyPart.CHEST}) {
            BodyPartState ps = m.part(p);
            if (ps.integrity() < 25) {
                for (Wound w : ps.wounds) if (!w.isDressed() && w.type != WoundType.BRUISE) return true;
            }
        }
        return false;
    }

    /** Сознание без учёта состояния «лежачий» — по ограничениям п. 4.5 ТЗ. */
    public static double rawConsciousness(MedicalState m, MedicalSettings s) {
        double c = 100;
        c = Math.min(c, stepLimit(m.pressure, 90, 80, 60, 40));
        c = Math.min(c, stepLimit(m.spo2, 90, 80, 60, 40));
        if (m.heart != Heart.NORMAL) c = 0;
        if (m.painShock) c = Math.min(c, 10);
        if (m.concussionKoSeconds > 0) c = Math.min(c, 10);
        // Разрушенная голова или грудь — нокдаун (решения, п. 1.13).
        if (m.part(BodyPart.HEAD).isDestroyed(s) || m.part(BodyPart.CHEST).isDestroyed(s))
            c = Math.min(c, s.destroyedVitalConsciousness);
        c = Math.min(c, 100 - m.concussion * 0.6);
        c = Math.min(c, 50 + m.brain * 0.5);
        if (m.morphineOverdoseSeconds > 0) c = Math.min(c, 50);
        double sedation = m.effect(DrugEffect.SEDATION);
        if (sedation > 0) c = Math.min(c, 100 - sedation);
        if (m.effect(DrugEffect.ANESTHESIA) > 0) c = 0;
        // Сепсис: от 30 % спутанность, 100 % — септический шок, человек падает.
        // Переохлаждение и перегрев туманят сознание.
        if (m.bodyTemp < 35) c = Math.min(c, 100 - (35 - m.bodyTemp) * 20);
        if (m.bodyTemp > 40) c = Math.min(c, 100 - (m.bodyTemp - 40) * 40);
        if (m.sepsis >= 100) c = Math.min(c, 20);
        else if (m.sepsis >= 30) c = Math.min(c, s.sepsisConsciousnessLimit);
        c = Math.min(c, Metabolism.consciousnessCap(m, s));
        c = Math.min(c, Substances.consciousnessCap(m, s));
        return clamp(c, 0, 100);
    }

    /** Ограничение: выше a — 100; a→b: 100→60; b→c: 60→30; c→d: 30→0. */
    static double stepLimit(double v, double a, double b, double c, double d) {
        if (v >= a) return 100;
        if (v >= b) return lerp(60, 100, (v - b) / (a - b));
        if (v >= c) return lerp(30, 60, (v - c) / (b - c));
        if (v >= d) return lerp(0, 30, (v - d) / (c - d));
        return 0;
    }

    /** Скорость таяния мозга в нокдауне, единиц в секунду. */
    public static double knockdownBrainRate(MedicalState m, MedicalSettings s, PatientTraits traits) {
        double severity = 1 - oxygenDelivery(m, s);
        double factor = traits.knockdownFactor(s);
        double slow = 100.0 / (s.knockdownMaxSeconds * factor);
        double fast = 100.0 / (s.knockdownMinSeconds * factor);
        double rate = lerp(slow, fast, clamp(severity, 0, 1));
        return m.stabilizedSeconds > 0 ? rate * s.stabilizationFactor : rate;
    }

    private static void tickBrainAndConsciousness(MedicalState m, StepInput in, MedicalSettings s, StepResult r) {
        double dt = in.dt;
        boolean threat = lifeThreat(m, s);
        double delivery = oxygenDelivery(m, s);

        switch (m.down) {
            case KNOCKDOWN -> {
                if (m.knockdownNoTimer && m.heart != Heart.NORMAL) m.knockdownNoTimer = false;
                if (threat && !m.knockdownNoTimer) m.brain -= knockdownBrainRate(m, s, in.traits) * dt;
            }
            case CLINICAL -> m.brain = Math.max(1, m.brain);
            default -> {
                // Гипоксия у бодрствующего или в обмороке.
                if (delivery < 0.6) m.brain -= s.hypoxiaBrainLossPerSecond * (0.6 - delivery) / 0.6 * 2 * dt;
            }
        }
        if (m.down == Down.NONE && delivery >= 0.9 && in.online && m.brain < 100 && !Organs.toxicBrain(m))
            m.brain = Math.min(100, m.brain + 100.0 / (s.brainRecoveryHours * 3600.0) * in.brainRecoveryFactor * dt);

        // Мозг изъят — смерть сразу, даже в клинической смерти (решения, п. 1.16).
        // Режим «без смерти» тут не спасает: клиническая смерть без мозга — бесконечные стоны (замечание живого теста 15).
        if (!m.hasOrgan(Organ.BRAIN)) {
            m.brain = 0;
            r.add(Event.DIED);
            return;
        }
        if (m.brain <= 0 && m.down != Down.CLINICAL) {
            brainDeath(m, s, r);
            return;
        }

        double raw = rawConsciousness(m, s);
        switch (m.down) {
            case NONE -> {
                m.consciousness = raw;
                if (raw < s.downedConsciousness) {
                    m.down = threat ? Down.KNOCKDOWN : Down.FAINT;
                    m.wakeSeconds = -1;
                    m.consciousness = Math.min(raw, 20);
                    r.add(Event.WENT_DOWN);
                }
            }
            case FAINT -> {
                if (threat) {
                    m.down = Down.KNOCKDOWN;
                    m.wakeSeconds = -1;
                    r.add(Event.FAINT_TO_KNOCKDOWN);
                } else if (raw >= s.downedConsciousness + 10 || m.wakeSeconds == 0) {
                    wake(m, r);
                    m.consciousness = raw;
                    return;
                }
                m.consciousness = Math.min(raw, 20);
            }
            case KNOCKDOWN -> {
                if (threat && !m.knockdownNoTimer) {
                    m.wakeSeconds = -1;
                } else if (!threat || m.heart == Heart.NORMAL && m.spo2 >= 60) {
                    if (m.wakeSeconds < 0) m.wakeSeconds = lerp(s.wakeMinSeconds, s.wakeMaxSeconds, in.random.nextDouble());
                    m.wakeSeconds = Math.max(0, m.wakeSeconds - dt);
                    if (m.wakeSeconds <= 0 && raw >= s.downedConsciousness + 10) {
                        wake(m, r);
                        m.consciousness = raw;
                        return;
                    }
                }
                m.consciousness = Math.min(raw, 20);
            }
            case CLINICAL -> {
                m.consciousness = 0;
                if (m.heart == Heart.NORMAL) {
                    // Реанимация удалась: обычный нокдаун без таймера, затем пробуждение.
                    m.down = Down.KNOCKDOWN;
                    m.brain = Math.max(m.brain, 10);
                    m.wakeSeconds = -1;
                    m.knockdownNoTimer = true;
                    m.postClinicalSeconds = s.postClinicalHours * 3600.0;
                    r.add(Event.RESCUED_FROM_CLINICAL);
                }
            }
        }
    }

    private static void wake(MedicalState m, StepResult r) {
        m.down = Down.NONE;
        m.wakeSeconds = -1;
        m.knockdownNoTimer = false;
        m.airway = false;
        r.add(Event.WOKE_UP);
    }

    /** Мозг погиб: в режиме «без смерти» — клиническая смерть, иначе смерть. */
    public static void brainDeath(MedicalState m, MedicalSettings s, StepResult r) {
        if (s.noDeathMode) {
            enterClinical(m);
            r.add(Event.CLINICAL_DEATH);
        } else {
            m.brain = 0;
            r.add(Event.DIED);
        }
    }

    public static void enterClinical(MedicalState m) {
        m.down = Down.CLINICAL;
        m.brain = 1;
        m.heart = Heart.ARREST;
        m.heartRate = 0;
        m.respiratoryArrest = true;
        m.consciousness = 0;
        m.wakeSeconds = -1;
    }

    // ------------------------------------------------------------------ утилиты

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
