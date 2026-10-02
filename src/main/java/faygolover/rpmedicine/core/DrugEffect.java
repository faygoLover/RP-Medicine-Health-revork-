package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;

/**
 * Эффекты лекарств второго этапа (ТЗ второго этапа, п. 6). Лекарства из датапака {@code drugs} состоят из
 * этих эффектов. Одинаковые эффекты не складываются по силе, а продлеваются по времени.
 * Порядок (ordinal) используется в сохранении — новые добавлять в конец.
 */
public enum DrugEffect {
    /** Обезболивание: сила вычитается из боли. */
    ANALGESIA("analgesia"),
    /** Жаропонижающее: сила 0–1 — доля снятой лихорадки. */
    ANTIPYRETIC("antipyretic"),
    /** Антибиотик: сила — на сколько %/ч замедляется рост инфекции (10 → рост 4 %/ч становится −6 %/ч). */
    ANTIBIOTIC("antibiotic"),
    /** Седация: сила — ограничение сознания сверху (100 − сила), снижает болевой шок и пульс. */
    SEDATION("sedation"),
    /** Давление: сила — прибавка к давлению (может быть отрицательной). */
    PRESSURE("pressure"),
    /** Пульс: сила — прибавка к пульсу (может быть отрицательной). */
    HEART_RATE("heart_rate"),
    /** Угнетение дыхания: сила 0–1 — доля ослабления дыхания. */
    RESP_DEPRESSION("resp_depression"),
    /** Свёртывание (как транексамовая кислота): сила 0–1 — доля снижения кровотечения. */
    COAGULATION("coagulation"),
    /** Быстрее проходит контузия: сила — множитель скорости. */
    CONCUSSION_RELIEF("concussion_relief");

    public static final DrugEffect[] VALUES = values();

    public final String id;

    DrugEffect(String id) {
        this.id = id;
    }

    public static Optional<DrugEffect> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (DrugEffect e : VALUES) if (e.id.equals(s)) return Optional.of(e);
        return Optional.empty();
    }

    public static DrugEffect byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : ANALGESIA;
    }

    /** Действующий эффект: сила, задержка до начала и оставшееся время, секунды. */
    public static final class Active {
        public double strength;
        public double delay;
        public double seconds;

        public Active(double strength, double delay, double seconds) {
            this.strength = strength;
            this.delay = delay;
            this.seconds = seconds;
        }

        public boolean working() {
            return delay <= 0 && seconds > 0;
        }

        public Active copy() {
            return new Active(strength, delay, seconds);
        }
    }
}
