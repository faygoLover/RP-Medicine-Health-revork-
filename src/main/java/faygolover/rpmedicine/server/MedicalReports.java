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

/** Полное состояние цифрами — для ГМа: команда {@code inspect} и ГМ-сканер. */
public final class MedicalReports {
    private MedicalReports() {}

    public static void sendFull(Player viewer, LivingEntity target) {
        for (Component c : full(target)) viewer.sendSystemMessage(c);
    }

    public static List<Component> full(LivingEntity target) {
        List<Component> out = new ArrayList<>();
        MedicalState m = Medical.state(target);
        if (m == null) {
            out.add(Component.translatable("rpmedicine.report.not_patient", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
            return out;
        }
        MedicalSettings s = MedicalSettings.get();
        out.add(Component.literal("== ").append(target.getDisplayName()).append(" ==").withStyle(ChatFormatting.GOLD));
        out.add(line("Состояние: %s, сознание %.0f, мозг %.1f, сердце %s%s", m.down, m.consciousness, m.brain, m.heart,
                m.respiratoryArrest ? ", дыхание остановлено" : ""));
        out.add(line("Кровь %.0f/%.0f мл (%.0f%%), физраствор %.0f мл, капельница %.0f мл; кровотечение наруж. %.0f, внутр. %.0f мл/мин",
                m.bloodVolume, m.normalBlood(s), m.bloodFraction(s) * 100, m.saline, m.salineDripRemaining,
                m.totalExternalBleed(s), m.totalInternalBleed()));
        out.add(line("Давление %.0f, пульс %.0f, дыхание %.0f, SpO2 %.0f (потолок %.0f), доставка O2 %.2f",
                m.pressure, m.heartRate, m.respRate, m.spo2, Physiology.spo2Ceiling(m, s), Physiology.oxygenDelivery(m, s)));
        out.add(line("Боль %.0f (без обезболивания %.0f), шок %.0f/%.0f%s, адреналин %.0f с, укол %.0f с",
                m.pain, m.rawPain, m.shockAccum, m.shockLimit, m.painShock ? " ОБМОРОК" : "", m.adrenalineSeconds, m.adrenalineInjectionSeconds));
        out.add(line("Обезболивающее %.0f с, морфин %.0f с%s, ТХК %.0f с, контузия %.0f, после клин. смерти %.0f мин",
                m.painkillerSeconds, m.morphineSeconds, m.morphineOverdoseSeconds > 0 ? " (передозировка)" : "", m.txaSeconds,
                m.concussion, m.postClinicalSeconds / 60));
        out.add(line("Пневмоторакс %s%s, воздуховод %s, Амбу %.0f с, СЛР %.0f с, рост %.0f см, вес %.0f кг",
                m.pneumo, m.pneumo == MedicalState.Pneumo.OPEN ? String.format(Locale.ROOT, " (до напряжённого %.0f с)", m.pneumoTimer)
                        : m.pneumo == MedicalState.Pneumo.TENSION ? String.format(Locale.ROOT, " (%.0f%%)", m.tensionProgress * 100) : "",
                m.airway ? "да" : "нет", m.ambuSeconds, m.cprSeconds, m.heightCm, m.weightKg));
        if (m.down == MedicalState.Down.KNOCKDOWN && Physiology.lifeThreat(m, s) && !m.knockdownNoTimer) {
            double rate = Physiology.knockdownBrainRate(m, s, Medical.traits(target));
            out.add(line("Нокдаун: осталось ~%.0f с", m.brain / Math.max(1e-6, rate)));
        }
        for (BodyPartState ps : m.parts) {
            if (ps.isHealthy()) continue;
            MutableComponent c = Component.translatable(ps.part.translationKey()).withStyle(ChatFormatting.AQUA);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, ": целостность %.0f", ps.integrity()));
            for (Wound w : ps.wounds) {
                sb.append(String.format(Locale.ROOT, "; %s %.1f (кровь %.0f мл/мин%s)", w.type.id, w.severity, w.bleed(s),
                        w.isDressed() ? ", " + w.dressing.id + (w.dressingQuality < 1 ? " плохо" : "") : ""));
            }
            if (ps.hasFracture()) sb.append("; перелом ").append(ps.fracture).append(ps.splint ? " (шина)" : "")
                    .append(String.format(Locale.ROOT, " %.0f%%", ps.fractureHeal * 100));
            if (ps.arterial) sb.append("; АРТЕРИЯ");
            if (ps.internalBleed > 0) sb.append(String.format(Locale.ROOT, "; внутреннее %.0f мл/мин", ps.internalBleed));
            if (ps.bullets > 0) sb.append("; пуль ").append(ps.bullets);
            if (ps.fragments > 0) sb.append("; осколков ").append(ps.fragments);
            if (ps.hasTourniquet()) sb.append(String.format(Locale.ROOT, "; жгут %s %.1f мин", ps.tourniquet, ps.tourniquetSeconds / 60));
            if (ps.ischemia > 0) sb.append(String.format(Locale.ROOT, "; ишемия %.0f", ps.ischemia));
            if (ps.occlusive) sb.append("; наклейка");
            out.add(c.append(Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE)));
        }
        return out;
    }

    private static Component line(String fmt, Object... args) {
        return Component.literal(String.format(Locale.ROOT, fmt, args)).withStyle(ChatFormatting.WHITE);
    }
}
