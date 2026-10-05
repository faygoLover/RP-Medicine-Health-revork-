package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Fracture;
import faygolover.rpmedicine.core.MedicalState.Down;
import faygolover.rpmedicine.core.MedicalState.Heart;
import faygolover.rpmedicine.core.MedicalState.Pneumo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Что видно при осмотре (п. 6.3 и 7 ТЗ). Цифр нет: только строки-ключи перевода с детализацией по
 * уровню медицины осматривающего. Результат уходит клиенту — поэтому здесь отсекается всё, что
 * осматривающему знать не положено.
 */
public final class Examination {
    private Examination() {}

    /** Строка осмотра: ключ перевода {@code rpmedicine.exam.<key>} и целые аргументы. */
    public record Line(String key, int[] args) {
        public Line(String key) {
            this(key, new int[0]);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Line l && l.key.equals(key) && java.util.Arrays.equals(l.args, args);
        }

        @Override
        public int hashCode() {
            return key.hashCode() * 31 + java.util.Arrays.hashCode(args);
        }
    }

    /** Осмотр части: цвет 0–4 (норма … критично) и строки. */
    public record PartView(BodyPart part, int color, List<Line> lines) {}

    public record View(List<PartView> parts, List<Line> general, int level) {
        @Override
        public boolean equals(Object o) {
            return o instanceof View v && v.level == level && v.parts.equals(parts) && v.general.equals(general);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parts, general, level);
        }
    }

    public static int bleedClass(double mlPerMin, MedicalSettings s) {
        if (mlPerMin <= 1) return 0;
        if (mlPerMin <= s.slightBleedMax) return 1;
        if (mlPerMin <= s.moderateBleedMax) return 2;
        if (mlPerMin <= s.heavyBleedMax) return 3;
        return 4;
    }

    /** Цвет части 0–4 по тому, что видно глазами: целостность, кровь, перелом. */
    public static int partColor(MedicalState m, BodyPartState ps, MedicalSettings s) {
        // 5 — часть разрушена или её нет (серая).
        if (ps.isDestroyed(s) || ps.missing && Limbs.stumpHealed(ps)) return 5;
        int c = 0;
        double integ = ps.integrity();
        if (integ < 95) c = 1;
        if (integ < 70) c = 2;
        if (integ < 45) c = 3;
        if (integ < 20) c = 4;
        int b = bleedClass(Physiology.partExternalBleed(m, ps, s), s);
        if (b >= 4) c = Math.max(c, 4);
        else if (b == 3) c = Math.max(c, 3);
        else if (b == 2) c = Math.max(c, 2);
        else if (b == 1) c = Math.max(c, 1);
        if (ps.hasFracture()) c = Math.max(c, ps.fracture == Fracture.OPEN ? 3 : 2);
        if (ps.dislocated) c = Math.max(c, 2);
        return c;
    }

    /**
     * @param level уровень медицины осматривающего
     * @param self  осмотр себя: детализация не выше {@code selfViewMaxLevel}
     */
    public static View examine(MedicalState m, int level, boolean self, MedicalSettings s) {
        int lvl = self ? Math.min(level, s.selfViewMaxLevel) : level;
        List<PartView> parts = new ArrayList<>();
        for (BodyPartState ps : m.parts) parts.add(new PartView(ps.part, partColor(m, ps, s), partLines(m, ps, lvl, self, s)));
        return new View(parts, general(m, lvl, self, s), lvl);
    }

    static List<Line> partLines(MedicalState m, BodyPartState ps, int lvl, boolean self, MedicalSettings s) {
        List<Line> out = new ArrayList<>();
        if (ps.missing) {
            out.add(new Line(Limbs.stumpHealed(ps) ? "stump_healed" : "stump"));
            if (ps.prosthesis != BodyPartState.Prosthesis.NONE)
                out.add(new Line("prosthesis_" + ps.prosthesis.name().toLowerCase(java.util.Locale.ROOT)));
        }
        if (ps.necrosis > 0 && !ps.missing) {
            if (lvl >= 4) out.add(new Line(Limbs.irreversible(ps, s) ? "necrosis_irreversible" : "necrosis_reversible", new int[]{(int) ps.necrosis}));
            else if (lvl >= 2) out.add(new Line("necrosis"));
            else out.add(new Line("skin_black"));
        }
        for (Wound w : ps.wounds) {
            if (lvl <= 0) {
                out.add(new Line("wound"));
            } else if (lvl == 1) {
                out.add(new Line("wound_type", new int[]{w.type.ordinal()}));
            } else {
                out.add(new Line("wound_typed_severity", new int[]{w.type.ordinal(), severityWord(w.severity)}));
            }
            if (w.type == WoundType.BURN && lvl >= 1) out.add(new Line("burn_degree", new int[]{w.burnDegree(s)}));
            // Признаки инфекции раны — с уровня 4 (второй этап, п. 3).
            if (w.isInfected() && lvl >= 4) out.add(new Line(w.infection >= 60 ? "wound_pus" : "wound_inflamed"));
            if (w.isDressed()) {
                // Качество повязки видно с уровня 2; промокает ли — видно всем.
                int q = lvl >= 2 ? (w.dressingQuality >= 0.85 ? 0 : w.dressingQuality >= 0.6 ? 1 : 2) : 0;
                out.add(new Line("dressing_" + w.dressing.id + (q > 0 ? "_q" : ""), new int[]{q}));
                double under = Physiology.isUnderTourniquet(m, ps.part) ? 0 : w.bleed(s);
                int uc = bleedClass(under, s);
                if (uc > 0) out.add(lvl <= 0 ? new Line("dressing_soaking") : new Line("dressing_seeping", new int[]{uc}));
            }
            if (w.sutured) out.add(new Line(w.weakSuture() && lvl >= 3 ? "suture_weak" : "sutured"));
        }
        double bleed = Physiology.partExternalBleed(m, ps, s);
        int bc = bleedClass(bleed, s);
        boolean arterial = ps.arterial && !Physiology.isUnderTourniquet(m, ps.part);
        if (arterial) bc = 4;
        if (bc > 0) {
            // Детализация по уровню: 0 — «кровит», 1–2 — сила, 3 — ещё и вид, 4+ — и мл/мин.
            if (lvl <= 0) out.add(new Line(bc >= 3 ? "bleeding_heavy" : "bleeding"));
            else if (lvl <= 2) out.add(new Line("bleeding_class_" + bc));
            else {
                int kind = arterial ? 2 : bc >= 2 ? 1 : 0;
                out.add(new Line("bleeding_kind", new int[]{bc, kind}));
                if (lvl >= 4) out.add(new Line("bleeding_rate", new int[]{(int) Math.round(bleed)}));
            }
        }
        if (ps.hasFracture()) {
            if (lvl <= 0) out.add(new Line("broken"));
            else if (ps.part == BodyPart.CHEST) out.add(new Line(lvl >= 2 ? "fracture_ribs" : "chest_pain_breathing"));
            else out.add(new Line(ps.fracture == Fracture.OPEN ? "fracture_open" : "fracture_closed"));
            if (ps.splint) out.add(new Line("splint"));
        }
        if (ps.hasTourniquet()) {
            out.add(new Line(ps.tourniquet == BodyPartState.Tourniquet.ESMARCH ? "esmarch" : "tourniquet",
                    new int[]{(int) (ps.tourniquetSeconds / 60)}));
        }
        if (ps.ischemia > 20 && lvl >= 1) out.add(new Line("ischemia"));
        // Вывих видно с уровня 4; ниже — просто «сустав деформирован».
        if (ps.dislocated) {
            if (lvl >= 4) out.add(new Line("dislocation_" + ps.part.kind.name().toLowerCase(java.util.Locale.ROOT)));
            else if (lvl >= 1) out.add(new Line("joint_deformed"));
            else out.add(new Line("broken"));
        }
        // Отторжение пересаженного органа видно анализами и врачу опытному.
        if (lvl >= 6) for (Organ o : Organ.VALUES)
            if (o.part == ps.part && (m.organRejection & o.bit()) != 0) out.add(new Line("organ_rejection", new int[]{o.ordinal()}));
        if (ps.surgery != BodyPartState.SurgeryStage.NONE)
            out.add(new Line("surgery_" + ps.surgery.name().toLowerCase(java.util.Locale.ROOT), new int[]{(int) (ps.surgeryOpenSeconds / 60)}));
        if (ps.fixated && ps.hasFracture() && lvl >= 1) out.add(new Line("fixated"));
        if (ps.occlusive) out.add(new Line("occlusive"));
        if (ps.localAnesthesiaSeconds > 0 && lvl >= 1) out.add(new Line("local_anesthesia"));
        if (lvl >= 2 && ps.internalBleed > 0) out.add(new Line("suspect_internal"));
        if (lvl >= 2 && ps.part == BodyPart.CHEST && m.pneumo != Pneumo.NONE) out.add(new Line("suspect_pneumothorax"));
        if (lvl >= 3 && ps.hasForeignBodies()) {
            if (ps.bullets > 0) out.add(new Line("bullet_inside"));
            if (ps.fragments > 0) out.add(new Line("fragments_inside"));
        }
        return out;
    }

    /** Тяжесть словами 0–3: лёгкая, средняя, тяжёлая, критическая. */
    public static int severityWord(double severity) {
        if (severity < 15) return 0;
        if (severity < 35) return 1;
        if (severity < 60) return 2;
        return 3;
    }

    static List<Line> general(MedicalState m, int lvl, boolean self, MedicalSettings s) {
        List<Line> out = new ArrayList<>();
        // Питание: истощение видно.
        if (Nutrition.anyLow(m, s) && lvl >= 1) out.add(new Line(Nutrition.low(m, Nutrition.PROTEIN, s) ? "malnourished" : "undernourished"));
        // Алкоголь и ломка (п. 9).
        if (m.intoxication > 10) out.add(new Line(m.intoxication > 40 ? "drunk" : "smells_alcohol"));
        for (Substance x : Substance.VALUES) {
            if (!Substances.withdrawal(m, x, s)) continue;
            out.add(lvl >= 3 ? new Line("withdrawal", new int[]{x.ordinal()}) : new Line("withdrawal_signs"));
            break;
        }
        if (m.seizureSeconds > 0) out.add(new Line("seizure"));
        // Сахар (диабет): холодный пот при низком, запах ацетона при высоком.
        if (m.bloodSugar < s.sugarLow) out.add(new Line("cold_sweat"));
        if (m.bloodSugar > s.sugarHigh && lvl >= 3) out.add(new Line("acetone_breath"));
        // Сознание
        if (!self) {
            int cons = m.down == Down.CLINICAL ? 3 : m.down != Down.NONE ? 2 : m.consciousness < s.dazedConsciousness ? 1 : 0;
            out.add(new Line("consciousness", new int[]{cons}));
        }
        // Цвет кожи: бледность от кровопотери, синюшность от гипоксии.
        double loss = 1 - m.bloodFraction(s);
        int skin = 0;
        if (loss >= 0.15) skin = 1;
        if (loss >= 0.30) skin = 2;
        if (m.spo2 < 85 || m.heart != Heart.NORMAL) skin = 3;
        if (!self) out.add(new Line("skin", new int[]{skin}));
        // Дыхание: есть/нет видно всем, частоту — на ощупь с уровня 3.
        boolean breathing = !m.respiratoryArrest && m.heart == Heart.NORMAL && m.respRate > 0;
        if (!self) {
            if (!breathing) out.add(new Line("breathing_none"));
            else if (lvl >= 3) out.add(new Line("breathing_rate", new int[]{rateWord(m.respRate, 10, 22)}));
            else out.add(new Line("breathing_present"));
        }
        // Пульс на ощупь — с уровня 3.
        if (!self && lvl >= 3) {
            if (m.heart != Heart.NORMAL || m.pressure < 50) out.add(new Line("pulse_none"));
            else out.add(new Line("pulse", new int[]{rateWord(m.heartRate, 55, 100), m.pressure < 85 ? 1 : 0}));
        }
        // Капельница видна всем; жар на ощупь — с уровня 4 (второй этап).
        if (m.intubated) out.add(new Line("intubated"));
        if (!self && lvl >= 4 && m.effect(DrugEffect.ANESTHESIA) > 0) out.add(new Line("anesthesia"));
        if (m.bloodDripRemaining > 0) out.add(new Line("drip_blood"));
        if (m.salineDripRemaining > 0) out.add(new Line("drip_saline"));
        if (!self && lvl >= 4) {
            if (m.bodyTemp >= 37.8) out.add(new Line("feels_hot"));
            else if (m.bodyTemp <= 35.5) out.add(new Line("feels_cold"));
            // Органы (третий этап): желтуха, отёки, напряжённый живот.
            if (m.organ(Organ.LIVER) >= 80) out.add(new Line("jaundice"));
            if (m.organ(Organ.HEART) >= 80 || m.organ(Organ.KIDNEYS) >= 80) out.add(new Line("edema"));
            if (m.organ(Organ.INTESTINES) >= 30) out.add(new Line("abdomen_tense"));
        }
        // Жалобы — только от того, кто в сознании (и свои ощущения).
        if (m.down == Down.NONE) {
            for (String k : complaints(m, s)) out.add(new Line("complaint_" + k));
        }
        if (lvl >= 2 && !self && m.down == Down.KNOCKDOWN && Physiology.lifeThreat(m, s)) out.add(new Line("dying"));
        return out;
    }

    /** 0 — редкий, 1 — норма, 2 — частый. */
    static int rateWord(double v, double low, double high) {
        return v < low ? 0 : v > high ? 2 : 1;
    }

    /** Жалобы и ощущения (п. 7.2 ТЗ). Ключи: {@code rpmedicine.exam.complaint_<k>}. */
    public static List<String> complaints(MedicalState m, MedicalSettings s) {
        List<String> c = new ArrayList<>();
        double loss = 1 - m.bloodFraction(s);
        if (m.pressure < 95 || loss >= 0.2 || m.concussion > 40) c.add("dizzy");
        if (m.spo2 < 92 || m.pneumo != Pneumo.NONE || m.respRate > 24) c.add("hard_to_breathe");
        if (loss >= 0.15) c.add("cold_fingers");
        for (BodyPart arm : new BodyPart[]{BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM}) {
            BodyPartState ps = m.part(arm);
            if (ps.hasFracture() || ps.integrity() < s.limbIntegrityThreshold || ps.ischemia > 20) {
                c.add(arm == BodyPart.RIGHT_ARM ? "right_arm_numb" : "left_arm_numb");
            }
        }
        BodyPart worst = null;
        double worstPain = 0;
        for (BodyPartState ps : m.parts) {
            double p = Physiology.partPain(m, ps, s);
            if (p > worstPain) {
                worstPain = p;
                worst = ps.part;
            }
        }
        if (worst != null && m.pain >= 15) c.add("pain_" + worst.id);
        if (m.concussion > 30 || m.part(BodyPart.ABDOMEN).internalBleed > 0 || m.nauseaSeconds > 0) c.add("nausea");
        if (m.concussion > 20) c.add("ringing");
        if (m.heartRate > 115) c.add("heart_pounding");
        if (m.postClinicalSeconds > 0 || loss >= 0.3 || m.sepsis >= 30) c.add("weak");
        if (m.bodyTemp >= 37.8) c.add("fever");
        if (m.transfusionReactionSeconds > 0) c.add("back_pain");
        // Голод, жажда, среда (второй этап, п. 12). Своя жажда — только без LSO, у LSO своя полоска.
        if (m.thirst < s.dehydrationThreshold * 100 + 10) c.add("thirsty");
        if (m.bodyTemp < 35.5) c.add("freezing");
        if (m.deafSeconds > 0) c.add("deaf");
        // Органы (третий этап).
        if (m.organ(Organ.LUNGS) >= 50 || m.organ(Organ.HEART) >= 50) c.add("cough");
        if (m.organ(Organ.LIVER) >= 50) c.add("side_pain");
        if (m.organ(Organ.KIDNEYS) >= 50 && !c.contains("nausea")) c.add("nausea");
        return c;
    }
}
