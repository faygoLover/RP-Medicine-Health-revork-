package faygolover.rpmedicine.core;

import java.util.Locale;

/**
 * Мини-игры лечения вне боя (ТЗ второго этапа, п. 9). Сервер решает, нужна ли мини-игра, выдаёт
 * задание (сид, сложность) и проверяет результат: время не меньше физически возможного, качество 0–1.
 */
public final class Minigames {
    private Minigames() {}

    public enum Type {
        /** Укол: нажать, когда метка в окне. */
        INJECTION(0.6),
        /** Вена (капельница, забор): два попадания в узкое окно. */
        VEIN(1.2),
        /** Перевязка: ровные круги мышью. */
        BANDAGE(2.0),
        /** Жгут: держать натяжение в зелёной зоне 3 с. */
        TOURNIQUET(3.0),
        /** Пинцет: провести по извилистому каналу, не задевая стенок. */
        TWEEZERS(1.5),
        /** Швы: попадать в точки по линии раны. */
        SUTURE(2.0),
        /** Вправление: рывок, когда стрелка в окне. */
        REDUCE(0.4);

        public static final Type[] VALUES = values();

        /** Быстрее этого мини-игру пройти нельзя, секунды (защита от подделки). */
        public final double minSeconds;

        Type(double minSeconds) {
            this.minSeconds = minSeconds;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type byOrdinal(int i) {
            return i >= 0 && i < VALUES.length ? VALUES[i] : INJECTION;
        }
    }

    /**
     * Мини-игра действия (null — только прогресс-бар). СЛР, мешок Амбу и дефибриллятор нужны лишь при
     * остановке сердца или дыхания, а это всегда «бой» (нокдаун с таймером или клиническая смерть).
     */
    public static Type typeFor(TreatmentAction a, Drug drug) {
        return switch (a) {
            case BANDAGE, PRESSURE_DRESSING, HEMOSTATIC -> Type.BANDAGE;
            case TOURNIQUET, ESMARCH -> Type.TOURNIQUET;
            case MORPHINE, ADRENALINE, TXA, NEEDLE -> Type.INJECTION;
            case SALINE, BLOOD_BAG, BLOOD_COLLECT, BLOOD_SAMPLE -> Type.VEIN;
            case TWEEZERS -> Type.TWEEZERS;
            case SUTURE -> Type.SUTURE;
            case REDUCE -> Type.REDUCE;
            case DRUG -> drug == null ? null : switch (drug.form()) {
                case INJECTION -> Type.INJECTION;
                case DRIP -> Type.VEIN;
                default -> null;
            };
            default -> null;
        };
    }

    /** Сложность 0–1 (1 — самая лёгкая: шире окно, медленнее стрелка) по уровню медицины. */
    public static double ease(int level, int minLevel) {
        return Physiology.clamp(0.35 + 0.1 * (level - minLevel), 0.15, 1.0);
    }

    /** «Бой»: медик или пациент получали урон недавно, пациент в нокдауне с таймером или клинической смерти. */
    public static boolean combat(MedicalState patient, boolean recentlyHurt, MedicalSettings s) {
        if (recentlyHurt) return true;
        if (patient.down == MedicalState.Down.CLINICAL) return true;
        return patient.down == MedicalState.Down.KNOCKDOWN && !patient.knockdownNoTimer && Physiology.lifeThreat(patient, s);
    }

    /** Ошибка по качеству мини-игры. */
    public static boolean failed(double quality, MedicalSettings s) {
        return quality < s.minigameFailQuality;
    }
}
