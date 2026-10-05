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
    /** Во сколько раз быстрее идёт капельница и переливание (стойка, койка). */
    public double dripFactor = 1.0;
    /** Множители условий (больничная койка и т.п.): заживление, восстановление крови, восстановление мозга. */
    public double healFactor = 1.0;
    public double bloodRegenFactor = 1.0;
    public double brainRecoveryFactor = 1.0;
    /** Множители условий для инфекции: шанс заражения ран и сила иммунитета (койка). */
    public double infectionRiskFactor = 1.0;
    public double immunityFactor = 1.0;
    /** Сдвиг температуры тела от среды (переохлаждение, перегрев из LSO), °C. */
    public double ambientTempShift;
    /** Дышит кислородом (койка рядом с источником кислорода). */
    public boolean oxygen;
    /** Аппарат ИВЛ рядом (операционный стол): интубированного вентилирует полностью. */
    public boolean ventilator;
    /** Вода 0–1 (своя жажда или LSO) и сытость 0–1 (ванильная еда). */
    public double hydration = 1.0;
    public double satiety = 1.0;
    public RandomGenerator random = new java.util.SplittableRandom();

    public StepInput() {}

    public StepInput(double dt) {
        this.dt = dt;
    }
}
