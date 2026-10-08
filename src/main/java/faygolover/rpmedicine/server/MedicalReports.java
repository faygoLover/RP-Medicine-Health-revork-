package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.BodyPartState;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Physiology;
import faygolover.rpmedicine.core.Wound;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Состояние цифрами — для ГМа: команда {@code inspect} и ГМ-сканер. Кратко — только отклонения от
 * нормы; {@code full} — всё. Значения подсвечены по тяжести: зелёный — норма, жёлтый и оранжевый —
 * отклонение, красный — опасно.
 */
public final class MedicalReports {
    private MedicalReports() {}

    public static void sendFull(Player viewer, LivingEntity target) {
        for (Component c : report(target, true)) viewer.sendSystemMessage(c);
    }

    public static List<Component> full(LivingEntity target) {
        return report(target, true);
    }

    /** Полный отчёт столбиком: по одному показателю с иконкой раздела на строку (панель ГМа). */
    public static List<Component> vertical(LivingEntity target) {
        List<Component> out = new ArrayList<>();
        for (Component line : report(target, true)) {
            String icon = line.getContents() instanceof net.minecraft.network.chat.contents.LiteralContents lc ? lc.text() : "";
            List<Component> sib = line.getSiblings();
            boolean rowLike = icon.length() <= 2 && !icon.isBlank() && sib.size() >= 3
                    && " ".equals(sib.get(0).getString());
            if (!rowLike) {
                out.add(line);
                continue;
            }
            // Строки вида «❤ метка значение  метка значение»: разделители — пробелы тёмно-серым.
            MutableComponent cur = null;
            for (Component c : sib) {
                String t = c.getString();
                if (t.equals(" ") || t.equals("  ")) {
                    if (cur != null) out.add(cur);
                    cur = Component.literal(icon + " ").withStyle(ChatFormatting.DARK_AQUA);
                    continue;
                }
                if (cur == null) cur = Component.literal(icon + " ").withStyle(ChatFormatting.DARK_AQUA);
                cur.append(c.copy());
            }
            if (cur != null) out.add(cur);
        }
        return out;
    }

    /** Строка «метка значение · метка значение …». */
    private static final class Row {
        final MutableComponent c;
        boolean any;

        Row(String title) {
            c = Component.literal(title).withStyle(ChatFormatting.DARK_AQUA);
        }

        Row add(String label, String value, ChatFormatting color) {
            c.append(Component.literal(any ? "  " : " ").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal(label + " ").withStyle(ChatFormatting.GRAY));
            c.append(Component.literal(value).withStyle(color));
            any = true;
            return this;
        }
    }

    private static final ChatFormatting OK = ChatFormatting.GREEN;
    private static final ChatFormatting MILD = ChatFormatting.YELLOW;
    private static final ChatFormatting MOD = ChatFormatting.GOLD;
    private static final ChatFormatting BAD = ChatFormatting.RED;
    private static final ChatFormatting CRIT = ChatFormatting.DARK_RED;

    /** Цвет по отклонению вниз: значение ≥ a — норма, ≥ b — жёлтый, ≥ c — оранжевый, иначе красный. */
    private static ChatFormatting low(double v, double a, double b, double c) {
        return v >= a ? OK : v >= b ? MILD : v >= c ? MOD : BAD;
    }

    /** Цвет по отклонению вверх. */
    private static ChatFormatting high(double v, double a, double b, double c) {
        return v <= a ? OK : v <= b ? MILD : v <= c ? MOD : BAD;
    }

    private static String f(String fmt, Object... a) {
        return String.format(Locale.ROOT, fmt, a);
    }

    public static List<Component> report(LivingEntity target, boolean all) {
        List<Component> out = new ArrayList<>();
        MedicalState m = Medical.state(target);
        if (m == null) {
            out.add(Component.translatable("rpmedicine.report.not_patient", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
            return out;
        }
        MedicalSettings s = MedicalSettings.get();
        // Заголовок: имя и главное состояние.
        MutableComponent head = Component.literal("━━ ").withStyle(ChatFormatting.DARK_GRAY).append(target.getDisplayName().copy().withStyle(ChatFormatting.GOLD));
        switch (m.down) {
            case FAINT -> head.append(Component.literal("  ◌ без сознания").withStyle(MOD));
            case KNOCKDOWN -> {
                head.append(Component.literal("  ▼ НОКДАУН").withStyle(BAD));
                if (Physiology.lifeThreat(m, s) && !m.knockdownNoTimer) {
                    double rate = Physiology.knockdownBrainRate(m, s, Medical.traits(target));
                    int sec = (int) Math.ceil(m.brain / Math.max(1e-6, rate));
                    head.append(Component.literal(f(" %d:%02d", sec / 60, sec % 60)).withStyle(BAD, ChatFormatting.BOLD));
                }
            }
            case CLINICAL -> head.append(Component.literal("  ☠ КЛИНИЧЕСКАЯ СМЕРТЬ").withStyle(CRIT, ChatFormatting.BOLD));
            default -> head.append(Component.literal("  ✔ в сознании").withStyle(OK));
        }
        if (m.heart != MedicalState.Heart.NORMAL) head.append(Component.literal("  ♥ " + (m.heart == MedicalState.Heart.ARREST ? "ОСТАНОВКА" : "ФИБРИЛЛЯЦИЯ")).withStyle(CRIT));
        if (m.respiratoryArrest) head.append(Component.literal("  ✖ не дышит").withStyle(CRIT));
        out.add(head);

        // Жизненные показатели.
        Row v = new Row("❤");
        double blood = m.bloodFraction(s) * 100;
        if (all || blood < 95) v.add("кровь", f("%.0f/%.0f мл (%.0f%%)", m.bloodVolume, m.normalBlood(s), blood), low(blood, 95, 85, 70));
        double ext = m.totalExternalBleed(s), in = m.totalInternalBleed();
        if (all || ext > 1) v.add("кровит", f("%.0f мл/мин", ext), high(ext, 1, 30, 150));
        if (all || in > 0) v.add("внутр.", f("%.0f мл/мин", in), high(in, 0, 20, 60));
        if (all || m.pressure < 100 || m.pressure > 145) v.add("АД", f("%.0f/%.0f", m.pressure, m.pressure * 0.65), m.pressure > 145 ? MILD : low(m.pressure, 100, 85, 70));
        if (all || m.heartRate < 55 || m.heartRate > 100) v.add("пульс", f("%.0f", m.heartRate), m.heartRate < 55 ? MOD : high(m.heartRate, 100, 125, 150));
        if (all || m.spo2 < 95) v.add("SpO₂", f("%.0f%% (потолок %.0f)", m.spo2, Physiology.spo2Ceiling(m, s)), low(m.spo2, 95, 90, 80));
        if (all || m.respRate < 10 || m.respRate > 22) v.add("ЧД", f("%.0f", m.respRate), m.respRate < 10 ? BAD : high(m.respRate, 22, 26, 30));
        if (v.any) out.add(v.c);

        Row n = new Row("☊");
        if (all || m.consciousness < 100) n.add("сознание", f("%.0f", m.consciousness), low(m.consciousness, 90, 60, 30));
        if (all || m.brain < 100) n.add("мозг", f("%.1f", m.brain), low(m.brain, 95, 70, 40));
        if (all || m.pain > 0 || m.rawPain > 0) n.add("боль", f("%.0f (без обезб. %.0f)", m.pain, m.rawPain), high(m.pain, 10, 30, 60));
        if (all || m.painShock || m.shockAccum > 0) n.add("шок", m.painShock ? "ОБМОРОК" : f("%.0f/%.0f", m.shockAccum, m.shockLimit), m.painShock ? BAD : MILD);
        if (all || m.concussion > 0) n.add("контузия", f("%.0f", m.concussion), high(m.concussion, 0, 30, 60));
        if (all || m.postClinicalSeconds > 0) n.add("после клин. смерти", f("%.0f мин", m.postClinicalSeconds / 60), MOD);
        if (n.any) out.add(n.c);

        Row o = new Row("✚");
        if (all || m.adrenalineSeconds > 0) o.add("адреналин", f("%.0f с", m.adrenalineSeconds), MILD);
        if (all || m.adrenalineInjectionSeconds > 0) o.add("укол адр.", f("%.0f с", m.adrenalineInjectionSeconds), MILD);
        if (all || m.painkillerSeconds > 0) o.add("таблетки", f("%.0f с", m.painkillerSeconds), OK);
        if (all || m.morphineSeconds > 0) o.add("морфин", f("%.0f с%s", m.morphineSeconds, m.morphineOverdoseSeconds > 0 ? " ПЕРЕДОЗ" : ""), m.morphineOverdoseSeconds > 0 ? BAD : OK);
        if (all || m.txaSeconds > 0) o.add("ТХК", f("%.0f с", m.txaSeconds), OK);
        if (all || m.stabilizedSeconds > 0) o.add("стабилизация", f("%.0f с", m.stabilizedSeconds), OK);
        if (all || m.saline > 0 || m.salineDripRemaining > 0) o.add("физраствор", f("%.0f мл (капает %.0f)", m.saline, m.salineDripRemaining), OK);
        if (m.bloodDripRemaining > 0) o.add("переливание", f("%.0f мл, %s%s", m.bloodDripRemaining, m.bloodDripType != null ? m.bloodDripType.label : "?",
                m.bloodDripSpoiled ? ", испорчен" : ""), m.bloodDripSpoiled ? BAD : OK);
        if (all || m.airway) o.add("воздуховод", m.airway ? "да" : "нет", OK);
        if (all || m.intubated) o.add("трубка", m.intubated ? "да" : "нет", OK);
        if (all || m.ambuSeconds > 0) o.add("Амбу", f("%.0f с", m.ambuSeconds), OK);
        if (all || m.cprSeconds > 0) o.add("СЛР", f("%.0f с", m.cprSeconds), OK);
        for (var e : m.effects.entrySet()) {
            var a = e.getValue();
            o.add(e.getKey().id, f("%.2f %s%.0f с", a.strength, a.delay > 0 ? f("(через %.0f) ", a.delay) : "", a.seconds), ChatFormatting.AQUA);
        }
        if (o.any) out.add(o.c);

        Row x = new Row("⚠");
        if (all || m.pneumo != MedicalState.Pneumo.NONE) {
            String pn = switch (m.pneumo) {
                case NONE -> "нет";
                case OPEN -> f("открытый (до напряжённого %.0f с)", m.pneumoTimer);
                case TENSION -> f("НАПРЯЖЁННЫЙ %.0f%%", m.tensionProgress * 100);
            };
            x.add("пневмоторакс", pn, m.pneumo == MedicalState.Pneumo.TENSION ? CRIT : m.pneumo == MedicalState.Pneumo.OPEN ? BAD : OK);
        }
        if (all || Math.abs(m.bodyTemp - 36.7) > 0.8) x.add("t°", f("%.1f", m.bodyTemp), m.bodyTemp >= 38.5 || m.bodyTemp <= 35 ? BAD : MILD);
        if (all || m.sepsis > 0) x.add("сепсис", f("%.0f%%", m.sepsis), high(m.sepsis, 0, 20, 50));
        if (m.transfusionReactionSeconds > 0) x.add("РЕАКЦИЯ на кровь", f("%.0f с", m.transfusionReactionSeconds), CRIT);
        if (m.spoiledBloodSeconds > 0) x.add("испорченная кровь", f("%.0f с", m.spoiledBloodSeconds), BAD);
        if (all || Math.abs(m.bloodSugar - faygolover.rpmedicine.core.Metabolism.NORMAL_SUGAR) > 0.5)
            x.add("сахар", f("%.1f", m.bloodSugar), m.bloodSugar < 3.5 || m.bloodSugar > 15 ? BAD : m.bloodSugar > 9 ? MILD : OK);
        for (faygolover.rpmedicine.core.Organ g : faygolover.rpmedicine.core.Organ.VALUES)
            if ((m.organRejection & g.bit()) != 0) x.add("отторжение", g.id, BAD);
        if (all || m.restrained) x.add("фиксация", m.restrained ? "да" : "нет", m.restrained ? MILD : OK);
        if (all || faygolover.rpmedicine.core.Nutrition.anyLow(m, s))
            x.add("питание", m.nutritionKnown ? f("Б %.0f Ж %.0f У %.0f В %.0f", m.nutrients[0], m.nutrients[1], m.nutrients[2], m.nutrients[3]) : "нет RP Culinary",
                    faygolover.rpmedicine.core.Nutrition.anyLow(m, s) ? BAD : faygolover.rpmedicine.core.Nutrition.balanced(m, s) ? OK : MILD);
        if (all || m.intoxication > 0) x.add("опьянение", f("%.0f", m.intoxication), high(m.intoxication, 20, 50, 80));
        if (m.seizureSeconds > 0) x.add("СУДОРОГИ", f("%.0f с", m.seizureSeconds), CRIT);
        for (faygolover.rpmedicine.core.Substance sub : faygolover.rpmedicine.core.Substance.VALUES) {
            double tol = m.tolerance[sub.ordinal()];
            boolean dep = faygolover.rpmedicine.core.Substances.dependent(m, sub);
            if (tol <= 0 && !dep) continue;
            boolean wd = faygolover.rpmedicine.core.Substances.withdrawal(m, sub, s);
            x.add(sub.id, f("толер. %.0f%%%s%s, %.1f ч без", tol, dep ? ", зависим." : "", wd ? ", ЛОМКА" : "", m.sinceDose[sub.ordinal()] / 3600),
                    wd ? BAD : dep ? MOD : MILD);
        }
        if (faygolover.rpmedicine.core.Organs.worst(m) > 0) {
            for (faygolover.rpmedicine.core.Organ g : faygolover.rpmedicine.core.Organ.VALUES) {
                if (!m.hasOrgan(g)) x.add(g.id, "ИЗЪЯТ", CRIT);
                else if (m.organ(g) > 0) x.add(g.id, f("%.0f%%", m.organ(g)), high(m.organ(g), 10, 40, 70));
            }
        }
        if (x.any) out.add(x.c);

        if (all) {
            Row p = new Row("ℹ");
            p.add("группа", m.bloodType != null ? m.bloodType.label : "не задана", ChatFormatting.WHITE);
            p.add("рост", f("%.0f см", m.heightCm), ChatFormatting.WHITE);
            p.add("вес", f("%.0f кг", m.weightKg), ChatFormatting.WHITE);
            out.add(p.c);
        }

        // Части тела: только повреждённые.
        for (BodyPartState ps : m.parts) {
            if (ps.isHealthy()) continue;
            double integ = ps.integrity();
            MutableComponent c = Component.literal(" ▪ ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(ps.part.translationKey()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(f(" %.0f", integ)).withStyle(integ <= 0 ? ChatFormatting.DARK_GRAY : low(integ, 80, 50, 25)));
            for (Wound w : ps.wounds) {
                c.append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY));
                c.append(Component.literal(f("%s %.0f", w.type.id, w.severity)).withStyle(ChatFormatting.WHITE));
                double b = w.bleed(s);
                if (b > 0.5) c.append(Component.literal(f(" ●%.0f", b)).withStyle(high(b, 30, 150, 300)));
                if (w.isDressed()) c.append(Component.literal(" ✚" + w.dressing.id + (w.dressingQuality < 1 ? f(" %.2f", w.dressingQuality) : ""))
                        .withStyle(w.dressingQuality < 0.6 ? MOD : ChatFormatting.AQUA));
                if (w.sutured) c.append(Component.literal(f(" швы %.2f", w.sutureQuality)).withStyle(ChatFormatting.AQUA));
                String inf = infection(w);
                if (!inf.isEmpty()) c.append(Component.literal(inf).withStyle(w.isInfected() ? BAD : ChatFormatting.GRAY));
            }
            if (ps.hasFracture()) c.append(Component.literal(f(" │ перелом %s%s %.0f%%", ps.fracture, ps.splint ? " (шина)" : "", ps.fractureHeal * 100)).withStyle(MOD));
            if (ps.dislocated) c.append(Component.literal(" │ вывих").withStyle(MOD));
            if (ps.arterial) c.append(Component.literal(" │ АРТЕРИЯ").withStyle(CRIT, ChatFormatting.BOLD));
            if (ps.internalBleed > 0) c.append(Component.literal(f(" │ внутр. %.0f мл/мин", ps.internalBleed)).withStyle(BAD));
            if (ps.bullets > 0) c.append(Component.literal(" │ пуль " + ps.bullets).withStyle(MOD));
            if (ps.fragments > 0) c.append(Component.literal(" │ осколков " + ps.fragments).withStyle(MOD));
            if (ps.hasTourniquet()) c.append(Component.literal(f(" │ жгут %s %.1f мин", ps.tourniquet, ps.tourniquetSeconds / 60))
                    .withStyle(ps.tourniquetSeconds >= 900 ? BAD : ChatFormatting.AQUA));
            if (ps.ischemia > 0) c.append(Component.literal(f(" │ ишемия %.0f", ps.ischemia)).withStyle(BAD));
            if (ps.occlusive) c.append(Component.literal(" │ наклейка").withStyle(ChatFormatting.AQUA));
            if (ps.surgery != faygolover.rpmedicine.core.BodyPartState.SurgeryStage.NONE)
                c.append(Component.literal(f(" │ операция: %s %.0f мин, загрязнение ×%.2f", ps.surgery, ps.surgeryOpenSeconds / 60, ps.surgeryContamination)).withStyle(MOD));
            if (ps.fixated) c.append(Component.literal(" │ остеосинтез").withStyle(ChatFormatting.AQUA));
            if (ps.necrosis > 0) c.append(Component.literal(f(" │ некроз %.0f%%", ps.necrosis)).withStyle(ps.necrosis >= s.necrosisIrreversible ? CRIT : BAD));
            if (ps.missing) c.append(Component.literal(" │ НЕТ ЧАСТИ").withStyle(CRIT));
            if (ps.prosthesis != faygolover.rpmedicine.core.BodyPartState.Prosthesis.NONE)
                c.append(Component.literal(" │ протез " + ps.prosthesis).withStyle(ChatFormatting.AQUA));
            if (ps.localAnesthesiaSeconds > 0) c.append(Component.literal(f(" │ анестезия %.0f с", ps.localAnesthesiaSeconds)).withStyle(ChatFormatting.AQUA));
            out.add(c);
        }
        if (!all) out.add(Component.literal("(всё — /rpmedicine inspect <цель> full)").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return out;
    }

    private static String infection(Wound w) {
        return switch (w.infectionStage) {
            case PENDING -> f(" (заражение через %.0f мин)", w.infectionTimer / 60);
            case INFECTED -> f(" ИНФЕКЦИЯ %.0f%%/иммун. %.0f%%", w.infection, w.immuneProgress);
            default -> "";
        };
    }
}
