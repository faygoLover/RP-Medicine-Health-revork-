package faygolover.rpmedicine.hospital;

import java.util.Locale;
import java.util.Optional;

/**
 * Функции госпиталя, которые мод даёт чужим блокам через датапак {@code rpmedicine/hospital_blocks}
 * (ТЗ второго этапа, п. 2.1). Своих блоков мебели мод не добавляет, кроме стойки капельницы (решения, п. 1.15).
 */
public enum HospitalFunction {
    /** Больничная койка: лечь, положить лежачего, заживление быстрее. */
    BED("bed", 0),
    /** Операционный стол: на втором этапе — как койка. */
    OPERATING_TABLE("operating_table", 0),
    /**
     * Стол с фиксацией (решения, п. 1.13): как операционный, но медик может зафиксировать пациента в
     * сознании — тот не встанет и не дёрнется; обезболивание не обязательно, боль остаётся.
     */
    RESTRAINT_TABLE("restraint_table", 0),
    /** Монитор показателей рядом с койкой. */
    MONITOR("monitor", 3),
    /** Стойка для капельницы: капельница идёт, пока пациент рядом. */
    IV_STAND("iv_stand", 3),
    /** Стерилизатор инструментов. */
    STERILIZER("sterilizer", 0),
    /** Холодильник для крови. */
    FRIDGE("fridge", 0),
    /** Лабораторный стол. */
    LAB("lab", 0),
    /** Источник кислорода. */
    OXYGEN("oxygen", 3);

    public static final HospitalFunction[] VALUES = values();

    public final String id;
    /** Радиус действия по умолчанию, блоки (0 — только сам блок). */
    public final int defaultRadius;

    HospitalFunction(String id, int defaultRadius) {
        this.id = id;
        this.defaultRadius = defaultRadius;
    }

    /** На блоке можно лежать. */
    public boolean isBed() {
        return this == BED || this == OPERATING_TABLE || this == RESTRAINT_TABLE;
    }

    public static Optional<HospitalFunction> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (HospitalFunction f : VALUES) if (f.id.equals(s)) return Optional.of(f);
        return Optional.empty();
    }
}
