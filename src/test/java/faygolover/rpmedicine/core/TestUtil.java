package faygolover.rpmedicine.core;

import java.util.Random;

/** Прогон физиологии в тестах. */
final class TestUtil {
    private TestUtil() {}

    static MedicalSettings settings() {
        return new MedicalSettings();
    }

    static StepInput input(long seed) {
        StepInput in = new StepInput(0.5);
        in.random = new Random(seed);
        return in;
    }

    /** Гоняет шаги, пока условие не выполнится; возвращает прошедшие секунды или -1. */
    static double runUntil(MedicalState m, StepInput in, MedicalSettings s, double maxSeconds, java.util.function.Predicate<MedicalState> cond) {
        double t = 0;
        while (t < maxSeconds) {
            Physiology.step(m, in, s);
            t += in.dt;
            if (cond.test(m)) return t;
        }
        return -1;
    }

    static void run(MedicalState m, StepInput in, MedicalSettings s, double seconds) {
        double t = 0;
        while (t < seconds) {
            Physiology.step(m, in, s);
            t += in.dt;
        }
    }
}
