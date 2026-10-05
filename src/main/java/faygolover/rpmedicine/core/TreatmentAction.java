package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;

/** Действия лечения (п. 6.2 ТЗ). Предметы из датапака {@code items} ссылаются на них по id. */
public enum TreatmentAction {
    BANDAGE("bandage", Target.PART),
    PRESSURE_DRESSING("pressure_dressing", Target.PART),
    HEMOSTATIC("hemostatic", Target.PART),
    TOURNIQUET("tourniquet", Target.PART),
    ESMARCH("esmarch", Target.PART),
    SPLINT("splint", Target.PART),
    OCCLUSIVE("occlusive", Target.PART),
    NEEDLE("needle", Target.PART),
    PAINKILLER("painkiller", Target.BODY),
    MORPHINE("morphine", Target.BODY),
    ADRENALINE("adrenaline", Target.BODY),
    TXA("txa", Target.BODY),
    SURGICAL_KIT("surgical_kit", Target.PART),
    SALINE("saline", Target.BODY),
    AMMONIA("ammonia", Target.BODY),
    AIRWAY("airway", Target.BODY),
    AMBU("ambu", Target.HOLD),
    DEFIBRILLATOR("defibrillator", Target.BODY),
    CPR("cpr", Target.HOLD),
    PULSE_OXIMETER("pulse_oximeter", Target.BODY),
    TONOMETER("tonometer", Target.BODY),
    // Второй этап
    /** Пакет крови: переливание (капает несколько минут). */
    BLOOD_BAG("blood_bag", Target.BODY),
    /** Пустой пакет: забор крови у донора. */
    BLOOD_COLLECT("blood_collect", Target.BODY),
    /** Препарат из датапака drugs: таблетки, укол, капельница. */
    DRUG("drug", Target.BODY),
    /** Наружное средство из датапака drugs: на часть тела. */
    DRUG_TOPICAL("drug_topical", Target.PART),
    // Диагностика (второй этап, п. 3)
    STETHOSCOPE("stethoscope", Target.BODY),
    THERMOMETER("thermometer", Target.BODY),
    SCANNER("scanner", Target.PART),
    /** Гемоанализатор: капля крови (тратит ланцет). */
    HEMOANALYZER("hemoanalyzer", Target.BODY),
    /** Шприц для забора: пробирка крови для лаборатории. */
    BLOOD_SAMPLE("blood_sample", Target.BODY),
    // Вывихи, пули, швы (второй этап, п. 7–8)
    /** Вправление вывиха — пустой рукой с панели. */
    REDUCE("reduce", Target.PART),
    TWEEZERS("tweezers", Target.PART),
    SUTURE("suture", Target.PART),
    SCISSORS("scissors", Target.PART),
    // Третий этап
    /** Интубация: трубка (нужен ларингоскоп в инвентаре). */
    INTUBATE("intubate", Target.BODY),
    /** Набор стабилизации: таймер нокдауна тает вдвое медленнее, один раз за нокдаун. */
    STABILIZE("stabilize", Target.BODY),
    // Пошаговая хирургия (третий этап, п. 4)
    /** Скальпель: вскрыть часть. */
    INCISE("incise", Target.PART),
    /** Зажим: пережать сосуды вскрытой части. */
    CLAMP("clamp", Target.PART),
    /** Ретрактор: раскрыть рану для специального шага. */
    RETRACT("retract", Target.PART),
    /** Сосудистый шов: сшить артерию на раскрытой части. */
    VESSEL_SUTURE("vessel_suture", Target.PART),
    /** Дрель и набор для остеосинтеза: зафиксировать перелом на раскрытой части. */
    OSTEOSYNTHESIS("osteosynthesis", Target.PART),
    /** Дренаж груди: снять пневмоторакс окончательно. */
    DRAIN("drain", Target.PART),
    /** Пила: ампутация раскрытой конечности (третий этап, п. 6.1). */
    AMPUTATE("amputate", Target.PART),
    /** Протез на зажившую культю (п. 6.2). */
    INSTALL_PROSTHESIS("install_prosthesis", Target.PART),
    /** Изъять орган раскрытой груди или живота в контейнер (п. 7.1). */
    ORGAN_REMOVE("organ_remove", Target.PART),
    /** Пересадить орган из контейнера. */
    TRANSPLANT("transplant", Target.PART),
    /** Пришить отнятую конечность. */
    REATTACH("reattach", Target.PART);

    /** Куда применяется: на часть тела, на человека целиком, удержанием. */
    public enum Target { PART, BODY, HOLD }

    public final String id;
    public final Target target;

    TreatmentAction(String id, Target target) {
        this.id = id;
        this.target = target;
    }

    /** Пациент должен стоять на месте всё время действия (забор и установка капельницы). */
    public boolean requiresStill() {
        return this == BLOOD_BAG || this == BLOOD_COLLECT || this == SALINE;
    }

    /** Диагностика: ошибка только от нехватки уровня, результат — в чат. */
    public boolean isDiagnostic() {
        return this == STETHOSCOPE || this == THERMOMETER || this == SCANNER || this == HEMOANALYZER;
    }

    public boolean isInstrument() {
        return this == PULSE_OXIMETER || this == TONOMETER;
    }

    public static Optional<TreatmentAction> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (TreatmentAction a : values()) if (a.id.equals(s)) return Optional.of(a);
        return Optional.empty();
    }
}
