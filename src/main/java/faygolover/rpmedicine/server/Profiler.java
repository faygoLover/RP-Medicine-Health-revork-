package faygolover.rpmedicine.server;

/** Замер нагрузки мода для {@code /rpmedicine profile}: время шагов физиологии за последние 20 секунд. */
public final class Profiler {
    private Profiler() {}

    private static final int WINDOW_TICKS = 400;
    private static long windowNanos;
    private static int windowSteps;
    private static int windowTicks;
    private static long lastNanos;
    private static int lastSteps;
    private static int lastTicks = 1;
    private static long extraNanos;

    public static void record(long nanos) {
        windowNanos += nanos;
        windowSteps++;
    }

    /** Прочая работа мода (урон, синхронизация) — учитывается отдельно. */
    public static void recordOther(long nanos) {
        extraNanos += nanos;
    }

    public static void serverTick() {
        if (++windowTicks >= WINDOW_TICKS) {
            lastNanos = windowNanos + extraNanos;
            lastSteps = windowSteps;
            lastTicks = windowTicks;
            windowNanos = 0;
            extraNanos = 0;
            windowSteps = 0;
            windowTicks = 0;
        }
    }

    /** Среднее время за тик, микросекунды. */
    public static double microsPerTick() {
        return lastNanos / 1000.0 / Math.max(1, lastTicks);
    }

    public static double stepsPerTick() {
        return lastSteps / (double) Math.max(1, lastTicks);
    }

    public static double microsPerStep() {
        return lastSteps == 0 ? 0 : lastNanos / 1000.0 / lastSteps;
    }
}
