package faygolover.rpmedicine.integration.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние речи игроков для голосового чата (п. 8 ТЗ). Обновляется в потоке сервера, читается в
 * потоке Simple Voice Chat — поэтому отдельная потокобезопасная таблица, а не медицинское состояние.
 * <p>
 * Задел на будущее: здесь же будут уровни искажения речи (одышка, травма челюсти, опьянение), которые
 * одинаково применяются к тексту и голосу. На первом этапе — только отключение микрофона и слуха.
 */
public final class VoiceState {
    private VoiceState() {}

    /** 0 — норма, 1 — микрофон отключён (нокдаун, обморок), 2 — отключено всё (клиническая смерть). */
    private static final Map<UUID, Byte> STATE = new ConcurrentHashMap<>();

    public static void set(UUID player, faygolover.rpmedicine.core.Speech speech) {
        byte v = (byte) speech.ordinal();
        if (v == 0) STATE.remove(player);
        else STATE.put(player, v);
    }

    public static void remove(UUID player) {
        STATE.remove(player);
    }

    public static boolean micMuted(UUID player) {
        return STATE.getOrDefault(player, (byte) 0) >= 1;
    }

    public static boolean deaf(UUID player) {
        return STATE.getOrDefault(player, (byte) 0) >= 2;
    }

    public static void clear() {
        STATE.clear();
    }
}
