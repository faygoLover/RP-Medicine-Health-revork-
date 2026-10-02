package faygolover.rpmedicine.core;

import java.util.random.RandomGenerator;

/** Что слой Minecraft сообщает физиологии за шаг пересчёта. */
public final class StepInput {
    /** Длительность шага, секунды. */
    public double dt = 0.5;
    public PatientTraits traits = PatientTraits.NONE;
    /** Игрок в сети (у заглушки false: заживление и восстановление не идут). */
    public boolean online = true;
    /** Секунд бега за шаг и число прыжков. */
    public double sprintSeconds;
    public int jumps;
    /** Пройдено блоков по земле за шаг. */
    public double distance;
    /** Нечем дышать: под водой без воздуха, в стене. */
    public boolean suffocating;
    /** Стоит на месте или лежит (капельница идёт только так). */
    public boolean still = true;
    public RandomGenerator random = new java.util.SplittableRandom();

    public StepInput() {}

    public StepInput(double dt) {
        this.dt = dt;
    }
}
