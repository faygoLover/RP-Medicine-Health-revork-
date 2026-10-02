package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Fracture;
import faygolover.rpmedicine.core.MedicalState.Heart;
import faygolover.rpmedicine.core.MedicalState.Pneumo;

import java.util.ArrayList;
import java.util.List;

/**
 * Диагностика второго этапа (ТЗ, п. 3): что показывают приборы. Словами — стетоскоп, сканер,
 * гемоанализатор; цифрами — термометр и лаборатория. Ключи слов: {@code rpmedicine.word.<k>}.
 */
public final class Diagnostics {
    private Diagnostics() {}

    /** Дыхание в стетоскоп. */
    public static String breathSound(MedicalState m) {
        if (m.respiratoryArrest || m.heart != Heart.NORMAL || m.respRate <= 0) return "breath_none";
        if (m.pneumo != Pneumo.NONE) return "breath_one_side_weak";
        BodyPartState chest = m.part(BodyPart.CHEST);
        if (chest.internalBleed > 0 || chest.hasFracture()) return "breath_crackles";
        if (m.respRate < 10) return "breath_shallow";
        if (m.respRate > 24) return "breath_fast";
        return "breath_normal";
    }

    /** Сердце в стетоскоп. */
    public static String heartSound(MedicalState m) {
        return switch (m.heart) {
            case ARREST -> "heart_none";
            case FIBRILLATION -> "heart_irregular";
            case NORMAL -> m.heartRate > 100 ? "heart_fast" : m.heartRate < 55 ? "heart_slow" : "heart_normal";
        };
    }

    /** Термометр: температура с точностью 0,1 °C. */
    public static double temperature(MedicalState m) {
        return Math.round(m.bodyTemp * 10) / 10.0;
    }

    /** Портативный сканер по части тела: внутреннее кровотечение, инородные тела, перелом. */
    public static List<String> scanPart(MedicalState m, BodyPart part) {
        BodyPartState ps = m.part(part);
        List<String> out = new ArrayList<>();
        out.add(ps.internalBleed > 0 ? "scan_internal_bleeding" : "scan_no_internal");
        if (ps.bullets > 0) out.add("scan_bullets_" + Math.min(ps.bullets, 5));
        if (ps.fragments > 0) out.add("scan_fragments_" + Math.min(ps.fragments, 5));
        if (!ps.hasForeignBodies()) out.add("scan_no_foreign");
        if (ps.hasFracture()) out.add(part == BodyPart.CHEST ? "scan_rib_fracture" : ps.fracture == Fracture.OPEN ? "scan_fracture_open" : "scan_fracture_closed");
        else out.add("scan_no_fracture");
        if (ps.dislocated) out.add("scan_dislocation");
        return out;
    }

    /** Гемоглобин словами по кислородной ёмкости крови. */
    public static String hemoglobinWord(MedicalState m, MedicalSettings s) {
        double cap = m.oxygenCapacity(s);
        if (cap >= 0.85) return "hb_normal";
        if (cap >= 0.6) return "hb_low";
        return "hb_very_low";
    }

    /** Признаки инфекции в крови: нет, местная, системная (сепсис). */
    public static String infectionWord(MedicalState m) {
        if (m.sepsis >= 10) return "infection_systemic";
        for (BodyPartState ps : m.parts) for (Wound w : ps.wounds) if (w.isInfected() && w.infection >= 20) return "infection_local";
        return "infection_none";
    }

    /** Полный анализ крови в лаборатории (цифрами). */
    public record Lab(BloodType bloodType, double hemoglobin, double leukocytes, boolean sepsis, double oxygenCapacity) {}

    public static Lab lab(MedicalState m, MedicalSettings s) {
        double cap = m.oxygenCapacity(s);
        double hb = Math.round(140 * Math.min(1.1, cap));
        double maxInf = 0;
        for (BodyPartState ps : m.parts) for (Wound w : ps.wounds) if (w.isInfected()) maxInf = Math.max(maxInf, w.infection);
        double wbc = 6.5 + maxInf * 0.08 + m.sepsis * 0.15;
        return new Lab(m.bloodType, hb, Math.round(wbc * 10) / 10.0, m.sepsis >= 10, cap);
    }
}
