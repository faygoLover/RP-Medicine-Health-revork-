package faygolover.rpmedicine.core;

import faygolover.rpmedicine.core.BodyPartState.Fracture;

import java.util.Iterator;

/** Заживление по времени игрока в сети (п. 6.4 ТЗ). Используется шагом физиологии и командой {@code time add}. */
public final class Healing {
    private Healing() {}

    /** Время заживления раны в секундах. */
    public static double woundHealSeconds(Wound w, BodyPartState ps, MedicalSettings s) {
        double t = w.peakSeverity / 100.0;
        double minutes = Physiology.lerp(s.healMinutesMin.getOrDefault(w.type, 30.0), s.healMinutesMax.getOrDefault(w.type, 60.0), Physiology.clamp(t, 0, 1));
        if (!w.isDressed()) minutes *= s.undressedHealFactor.getOrDefault(w.type, 2.0);
        if (w.bandageBoost) minutes /= 1.25;
        return Math.max(1, minutes * 60.0);
    }

    /** Заживает ли рана сейчас: огнестрел — пока пуля внутри, осколочная — пока осколки внутри — нет. */
    public static boolean canHeal(Wound w, BodyPartState ps) {
        if (w.type == WoundType.GUNSHOT && ps.bullets > 0) return false;
        if (w.type == WoundType.SHRAPNEL && ps.fragments > 0) return false;
        return true;
    }

    public static double fractureHealSeconds(BodyPartState ps, MedicalSettings s) {
        if (ps.part == BodyPart.CHEST) return s.ribHealMinutes * 60.0;
        double q = ps.splintQuality;
        double minutes = Physiology.lerp(s.fractureHealMinutesMin, s.fractureHealMinutesMax, ps.fracture == Fracture.OPEN ? 1 : 0.5);
        return minutes * 60.0 / Math.max(0.25, q);
    }

    /** Продвигает заживление на {@code seconds} секунд в сети. */
    public static void advance(MedicalState m, double seconds, MedicalSettings s) {
        double speed = s.healSpeedMultiplier * (m.healBoostSeconds > 0 ? 1.5 : 1.0);
        double dt = seconds * speed;
        for (BodyPartState ps : m.parts) {
            boolean hadWounds = !ps.wounds.isEmpty();
            Iterator<Wound> it = ps.wounds.iterator();
            while (it.hasNext()) {
                Wound w = it.next();
                if (!canHeal(w, ps)) continue;
                double rate = w.peakSeverity / woundHealSeconds(w, ps, s);
                w.severity -= rate * dt;
                if (w.severity <= 0.01) it.remove();
            }
            // Артерия: тампонада гемостатиком убирает флаг сразу; иначе — когда зажила последняя рана части.
            if (ps.arterial && hadWounds && ps.wounds.isEmpty()) ps.arterial = false;

            if (ps.hasFracture()) {
                boolean heals = ps.part == BodyPart.CHEST || ps.splint;
                if (heals) {
                    ps.fractureHeal += dt / fractureHealSeconds(ps, s);
                    if (ps.fractureHeal >= 1) {
                        ps.fracture = Fracture.NONE;
                        ps.fractureHeal = 0;
                    }
                }
            }
        }
    }

    /**
     * Прокрутка времени командой {@code /rpmedicine time add}: заживление и восстановление крови
     * (только при отсутствии кровотечения). Идёт шагами по минуте, чтобы раны успевали закрываться.
     */
    public static void fastForward(MedicalState m, double seconds, MedicalSettings s) {
        double left = seconds;
        while (left > 0) {
            double dt = Math.min(60, left);
            left -= dt;
            advance(m, dt, s);
            for (BodyPartState ps : m.parts) {
                for (Wound w : ps.wounds) if (w.isDressed()) w.dressingAge += dt;
                if (!ps.hasTourniquet() && ps.ischemia > 0)
                    ps.ischemia = Math.max(0, ps.ischemia - s.ischemiaRecoveryPerMinute * dt / 60.0);
            }
            if (m.totalExternalBleed(s) + m.totalInternalBleed() < 0.01)
                Physiology.regenerateBlood(m, s.bloodRegenPerHour * dt / 3600.0, s);
            if (m.down == MedicalState.Down.NONE && m.brain < 100)
                m.brain = Math.min(100, m.brain + 100.0 / (s.brainRecoveryHours * 3600.0) * dt);
            m.postClinicalSeconds = Math.max(0, m.postClinicalSeconds - dt);
        }
    }
}
