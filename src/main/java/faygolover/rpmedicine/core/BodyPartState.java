package faygolover.rpmedicine.core;

import java.util.ArrayList;
import java.util.List;

/** Состояние одной части тела (п. 2.1 ТЗ). */
public final class BodyPartState {
    public enum Fracture {
        NONE, CLOSED, OPEN;

        public static Fracture byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NONE;
        }
    }

    public enum Tourniquet {
        NONE, CAT, ESMARCH;

        public static Tourniquet byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NONE;
        }
    }

    public final BodyPart part;
    public final List<Wound> wounds = new ArrayList<>();

    public Fracture fracture = Fracture.NONE;
    public boolean splint;
    /** Качество шины 0–1. */
    public double splintQuality = 1.0;
    /** Заживление перелома 0–1. */
    public double fractureHeal;

    /** Инородные тела внутри: пули и осколки. */
    public int bullets;
    public int fragments;

    /** Артериальное кровотечение. Останавливается жгутом или тампонадой гемостатиком. */
    public boolean arterial;
    /** Внутреннее кровотечение, мл/мин (грудь и живот). */
    public double internalBleed;

    public Tourniquet tourniquet = Tourniquet.NONE;
    /** Сколько секунд в сети стоит жгут. */
    public double tourniquetSeconds;
    /** Ишемия от жгута 0–100: снижает целостность и болит. */
    public double ischemia;

    /** Окклюзионная наклейка (грудь). */
    public boolean occlusive;

    public BodyPartState(BodyPart part) {
        this.part = part;
    }

    public double totalSeverity() {
        double sum = 0;
        for (Wound w : wounds) sum += w.severity;
        return sum;
    }

    /** Целостность 0–100: 100 минус сумма тяжести ран и ишемия. */
    public double integrity() {
        return Math.max(0.0, 100.0 - totalSeverity() - ischemia);
    }

    public boolean hasFracture() {
        return fracture != Fracture.NONE;
    }

    public boolean hasForeignBodies() {
        return bullets > 0 || fragments > 0;
    }

    public boolean hasTourniquet() {
        return tourniquet != Tourniquet.NONE;
    }

    /** Есть ли что-то, что требует пересчёта (иначе часть «здорова»). */
    public boolean isHealthy() {
        return wounds.isEmpty() && fracture == Fracture.NONE && bullets == 0 && fragments == 0 && !arterial
                && internalBleed <= 0 && tourniquet == Tourniquet.NONE && ischemia <= 0 && !occlusive && !splint;
    }

    public boolean anyDressing() {
        for (Wound w : wounds) if (w.isDressed()) return true;
        return false;
    }

    public void clear() {
        wounds.clear();
        fracture = Fracture.NONE;
        splint = false;
        splintQuality = 1.0;
        fractureHeal = 0;
        bullets = 0;
        fragments = 0;
        arterial = false;
        internalBleed = 0;
        tourniquet = Tourniquet.NONE;
        tourniquetSeconds = 0;
        ischemia = 0;
        occlusive = false;
    }

    public void copyFrom(BodyPartState o) {
        wounds.clear();
        for (Wound w : o.wounds) wounds.add(w.copy());
        fracture = o.fracture;
        splint = o.splint;
        splintQuality = o.splintQuality;
        fractureHeal = o.fractureHeal;
        bullets = o.bullets;
        fragments = o.fragments;
        arterial = o.arterial;
        internalBleed = o.internalBleed;
        tourniquet = o.tourniquet;
        tourniquetSeconds = o.tourniquetSeconds;
        ischemia = o.ischemia;
        occlusive = o.occlusive;
    }
}
