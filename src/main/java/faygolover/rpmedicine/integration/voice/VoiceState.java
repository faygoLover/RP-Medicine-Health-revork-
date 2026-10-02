package faygolover.rpmedicine.integration.voice;

import faygolover.rpmedicine.core.Speech;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние речи игроков для голосового чата (п. 8 ТЗ). Обновляется в потоке сервера, читается в
 * потоке Simple Voice Chat — поэтому отдельная потокобезопасная таблица, а не медицинское состояние.
 * Одышка (второй этап) — обрывы: часть пакетов микрофона выбрасывается.
 */
public final class VoiceState {
    private VoiceState() {}

    /** Речь и сила одышки 0–1. */
    private record Entry(Speech speech, double breath) {}

    private static final Map<UUID, Entry> STATE = new ConcurrentHashMap<>();
    /** Счётчик пакетов микрофона (20 мс каждый) — для ритма обрывов. */
    private static final Map<UUID, Integer> FRAMES = new ConcurrentHashMap<>();

    public static void set(UUID player, Speech speech, double breath) {
        if (speech == Speech.NORMAL) {
            STATE.remove(player);
            FRAMES.remove(player);
        } else {
            STATE.put(player, new Entry(speech, breath));
        }
    }

    public static void remove(UUID player) {
        STATE.remove(player);
        FRAMES.remove(player);
    }

    public static boolean micMuted(UUID player) {
        Entry e = STATE.get(player);
        return e != null && (e.speech == Speech.MUTED || e.speech == Speech.SILENCED);
    }

    public static boolean deaf(UUID player) {
        Entry e = STATE.get(player);
        return e != null && e.speech == Speech.SILENCED;
    }

    /**
     * Одышка: выбросить ли этот пакет микрофона. Цикл 1,2 с (60 пакетов): говорит, потом обрыв на вдох —
     * от 0,2 с при слабой одышке до 0,7 с при сильной.
     */
    public static boolean dropBreathless(UUID player) {
        Entry e = STATE.get(player);
        if (e == null || e.speech != Speech.BREATHLESS) return false;
        int frame = FRAMES.merge(player, 1, (a, b) -> (a + b) % 60);
        int gap = (int) Math.round(10 + 25 * e.breath);
        return frame >= 60 - gap;
    }

    public static void clear() {
        STATE.clear();
        FRAMES.clear();
    }
}
