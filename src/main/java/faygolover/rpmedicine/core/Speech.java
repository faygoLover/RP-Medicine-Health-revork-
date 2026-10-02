package faygolover.rpmedicine.core;

/**
 * Общий механизм речи для текстового чата и голоса (п. 8 ТЗ). На первом этапе — только отключение;
 * искажения (тише, с обрывами, глуше) от одышки, травмы челюсти, опьянения добавятся на следующих
 * этапах сюда же, чтобы одинаково влиять на текст и голос.
 */
public enum Speech {
    /** Говорит нормально. */
    NORMAL,
    /** Лежачий: в чат уходит «...», микрофон отключён, слышит. */
    MUTED,
    /** Клиническая смерть: ни говорить, ни слышать, чат недоступен. */
    SILENCED;

    public static Speech of(MedicalState m) {
        return switch (m.down) {
            case NONE -> NORMAL;
            case FAINT, KNOCKDOWN -> MUTED;
            case CLINICAL -> SILENCED;
        };
    }

    public boolean canHear() {
        return this != SILENCED;
    }

    public boolean canSpeak() {
        return this == NORMAL;
    }
}
