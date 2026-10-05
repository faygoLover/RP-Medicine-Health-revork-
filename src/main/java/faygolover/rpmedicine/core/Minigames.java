package faygolover.rpmedicine.core;

import java.util.Locale;

/**
 * Мини-игры лечения вне боя (ТЗ второго этапа, п. 9). Сервер решает, нужна ли мини-игра, выдаёт
 * задание (сид, сложность) и проверяет результат: время не меньше физически возможного, качество 0–1.
 */
public final class Minigames {
    private Minigames() {}

    public enum Type {
        /** Инъекция: навести иглу на место укола, затем ровно давить на поршень. */
        INJECTION(1.8),
        /** Венепункция (капельница, забор): провести иглу по вене, не задевая стенок, и удержать. */
        VEIN(2.0),
        /** Перевязка: круги мышью в указанную сторону, направление меняется. */
        BANDAGE(2.4),
        /** Жгут: держать натяжение в плывущей зелёной зоне. */
        TOURNIQUET(3.0),
        /** Пинцет: довести до пули по каналу и вытащить обратно, не задевая стенок. */
        TWEEZERS(2.0),
        /** Швы: протягивать нить от точки к парной точке, не уводя иглу. */
        SUTURE(2.4),
        /** Вправление: вытяжение, затем рывок точно в окне. */
        REDUCE(1.4),
        // Операция (решения, п. 1.14): одна сцена тела, шаги продолжают друг друга.
        /** Разрез: провести скальпелем по линии ровно и не спеша. */
        INCISION(1.6),
        /** Зажимы: поймать пульсирующие точки кровотечения. */
        CLAMP(1.5),
        /** Ретракторы: медленно развести края раны до меток. */
        RETRACT(1.4),
        /** Закрыть операцию: стежки по разрезу. */
        CLOSE(2.4),
        /** Внутреннее кровотечение: удержать иглу на источнике, пока он ушивается. */
        BLEED_SUTURE(2.0),
        /** Орган: стежки по разрыву органа. */
        ORGAN_SUTURE(2.2),
        /** Сосудистый шов: провести нить от одного конца артерии к другому по узкому каналу. */
        VESSEL(2.0),
        /** Остеосинтез: навести дрель на отверстие под винт и сверлить с нужным усилием. */
        DRILL(2.4),
        /** Дренаж: попасть в межрёберный промежуток и ввести трубку. */
        DRAIN(1.8),
        /** Пули и осколки из раскрытой раны — пинцетом на лоток. */
        EXTRACT(1.0),
        /** Ампутация: пилить ровными движениями, не выходя из распила. */
        AMPUTATION(2.6);

        public static final Type[] VALUES = values();

        /** Быстрее этого мини-игру пройти нельзя, секунды (защита от подделки). */
        public final double minSeconds;

        Type(double minSeconds) {
            this.minSeconds = minSeconds;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Мини-игра операции: идёт всегда (и в бою), рисуется сценой тела. */
        public boolean isSurgical() {
            return ordinal() >= INCISION.ordinal();
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

    /**
     * Что видно на сцене операции: часть тела, стадия до шага, постоянный сид сцены (разрез и точки
     * кровотечения на тех же местах во всех шагах), признаки и число инородных тел.
     */
    public record Scene(int part, int stage, long seed, int flags, int bullets, int fragments) {
        public static final Scene NONE = new Scene(-1, 0, 0, 0, 0, 0);
        public static final int ARTERIAL = 1, FRACTURE = 2, INTERNAL = 4, ORGAN = 8, PNEUMO = 16, FIXATED = 32;

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        public static Scene of(MedicalState m, BodyPart part, long seed) {
            BodyPartState ps = m.part(part);
            int f = 0;
            if (ps.arterial) f |= ARTERIAL;
            if (ps.hasFracture()) f |= FRACTURE;
            if (ps.internalBleed > 0) f |= INTERNAL;
            for (Organ o : Organ.VALUES) if (o.part == part && m.organs[o.ordinal()] > 0) f |= ORGAN;
            if (m.pneumo != MedicalState.Pneumo.NONE) f |= PNEUMO;
            if (ps.fixated) f |= FIXATED;
            return new Scene(part.ordinal(), ps.surgery.ordinal(), seed, f, ps.bullets, ps.fragments);
        }
    }

    /**
     * Мини-игра шага операции по состоянию части (null — не операция). Швы на вскрытой части — по тому,
     * что они сделают: кровотечение, орган или закрыть.
     */
    public static Type surgeryType(TreatmentAction a, MedicalState m, BodyPartState ps) {
        return switch (a) {
            case INCISE -> Type.INCISION;
            case CLAMP -> Type.CLAMP;
            case RETRACT -> Type.RETRACT;
            case VESSEL_SUTURE -> Type.VESSEL;
            case OSTEOSYNTHESIS -> Type.DRILL;
            case DRAIN -> Type.DRAIN;
            case AMPUTATE -> Type.AMPUTATION;
            case TWEEZERS -> ps.surgery == BodyPartState.SurgeryStage.RETRACTED && ps.hasForeignBodies() ? Type.EXTRACT : null;
            case SUTURE -> {
                if (ps.surgery == BodyPartState.SurgeryStage.NONE) yield null;
                if (ps.surgery == BodyPartState.SurgeryStage.RETRACTED) {
                    if (ps.internalBleed > 0) yield Type.BLEED_SUTURE;
                    for (Organ o : Organ.VALUES) if (o.part == ps.part && m.organs[o.ordinal()] > 0) yield Type.ORGAN_SUTURE;
                }
                yield Type.CLOSE;
            }
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
