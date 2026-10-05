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

    /** Ход операции на части (третий этап, п. 4.1). Порядок — в сохранении. */
    public enum SurgeryStage {
        NONE, OPEN, CLAMPED, RETRACTED;

        public static SurgeryStage byOrdinal(int i) {
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
    /** Сколько секунд в сети инородные тела внутри (второй этап: долго внутри — выше шанс заражения). */
    public double foreignBodySeconds;

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

    /** Вывих (второй этап, п. 7): плечо, локоть, колено, голеностоп. */
    public boolean dislocated;
    /** Местная анестезия этой части (третий этап, п. 3), секунд. */
    public double localAnesthesiaSeconds;

    /** Операция на части: вскрыто, зажато, раскрыто (третий этап, п. 4). */
    public SurgeryStage surgery = SurgeryStage.NONE;
    /** Сколько секунд часть открыта; счётчик минутной проверки заражения. */
    public double surgeryOpenSeconds;
    public double surgeryCheckSeconds;
    /** Загрязнение операции: множитель шанса заражения открытой части (место, инструменты, экипировка). */
    public double surgeryContamination = 1.0;
    /** Перелом зафиксирован остеосинтезом: заживает быстрее, шина не нужна. */
    public boolean fixated;

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

    /** Часть разрушена: целостность почти ноль (заживление на доли процента её не «чинит»). */
    public boolean isDestroyed(MedicalSettings s) {
        return integrity() < s.destroyedIntegrity;
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
                && internalBleed <= 0 && tourniquet == Tourniquet.NONE && ischemia <= 0 && !occlusive && !splint && !dislocated
                && localAnesthesiaSeconds <= 0 && surgery == SurgeryStage.NONE && !fixated;
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
        foreignBodySeconds = 0;
        arterial = false;
        internalBleed = 0;
        tourniquet = Tourniquet.NONE;
        tourniquetSeconds = 0;
        ischemia = 0;
        occlusive = false;
        dislocated = false;
        localAnesthesiaSeconds = 0;
        surgery = SurgeryStage.NONE;
        surgeryOpenSeconds = 0;
        surgeryCheckSeconds = 0;
        surgeryContamination = 1.0;
        fixated = false;
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
        foreignBodySeconds = o.foreignBodySeconds;
        arterial = o.arterial;
        internalBleed = o.internalBleed;
        tourniquet = o.tourniquet;
        tourniquetSeconds = o.tourniquetSeconds;
        ischemia = o.ischemia;
        occlusive = o.occlusive;
        dislocated = o.dislocated;
        localAnesthesiaSeconds = o.localAnesthesiaSeconds;
        surgery = o.surgery;
        surgeryOpenSeconds = o.surgeryOpenSeconds;
        surgeryCheckSeconds = o.surgeryCheckSeconds;
        surgeryContamination = o.surgeryContamination;
        fixated = o.fixated;
    }
}
