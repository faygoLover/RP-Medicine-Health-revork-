package faygolover.rpmedicine.core;

import java.util.random.RandomGenerator;

/**
 * Общий механизм речи для текстового чата и голоса (п. 8 ТЗ). Одышка (второй этап) — речь с
 * обрывами: в тексте слова перемежаются «...», в голосе пропадают куски. Травма челюсти и опьянение
 * добавятся сюда же, чтобы одинаково влиять на текст и голос.
 */
public enum Speech {
    /** Говорит нормально. */
    NORMAL,
    /** Одышка: говорит с обрывами, по нескольку слов. */
    BREATHLESS,
    /** Лежачий: в чат уходит «...», микрофон отключён, слышит. */
    MUTED,
    /** Клиническая смерть: ни говорить, ни слышать, чат недоступен. */
    SILENCED;

    public static Speech of(MedicalState m) {
        return of(m, MedicalSettings.get());
    }

    public static Speech of(MedicalState m, MedicalSettings s) {
        return switch (m.down) {
            case NONE -> breathlessness(m, s) > 0 ? BREATHLESS : NORMAL;
            case FAINT, KNOCKDOWN -> MUTED;
            case CLINICAL -> SILENCED;
        };
    }

    /** Одышка 0–1: частое дыхание или нехватка кислорода. */
    public static double breathlessness(MedicalState m, MedicalSettings s) {
        double byRate = (m.respRate - s.breathlessRespRate) / 10.0;
        double bySpo2 = (s.breathlessSpo2 - m.spo2) / 15.0;
        double v = Math.max(byRate, bySpo2);
        return v <= 0 ? 0 : Math.min(1, 0.2 + v);
    }

    public boolean canHear() {
        return this != SILENCED;
    }

    public boolean canSpeak() {
        return this == NORMAL || this == BREATHLESS;
    }

    /**
     * Речь с обрывами: после каждых нескольких слов — «...». Чем сильнее одышка, тем короче куски
     * (от 4 слов при слабой до 1 при сильной).
     */
    public static String breathless(String text, double level, RandomGenerator rnd) {
        String[] words = text.trim().split("\\s+");
        if (words.length <= 1 || level <= 0) return text;
        int maxRun = Math.max(1, (int) Math.round(4 - 3 * level));
        StringBuilder out = new StringBuilder();
        int run = 0;
        int limit = 1 + rnd.nextInt(maxRun);
        for (int i = 0; i < words.length; i++) {
            if (i > 0) out.append(' ');
            out.append(words[i]);
            if (++run >= limit && i < words.length - 1) {
                out.append("...");
                run = 0;
                limit = 1 + rnd.nextInt(maxRun);
            }
        }
        return out.toString();
    }
}
