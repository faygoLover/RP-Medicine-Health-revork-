package faygolover.rpmedicine.core;

import java.util.EnumSet;

/** События шага физиологии, на которые реагирует слой Minecraft. */
public final class StepResult {
    public enum Event {
        /** Упал: обморок или нокдаун. */
        WENT_DOWN,
        /** Обморок перешёл в нокдаун (появилась угроза жизни). */
        FAINT_TO_KNOCKDOWN,
        /** Встал. */
        WOKE_UP,
        /** Клиническая смерть (режим «без смерти»). */
        CLINICAL_DEATH,
        /** Вытащили из клинической смерти. */
        RESCUED_FROM_CLINICAL,
        /** Мозг погиб: настоящая смерть. */
        DIED,
        HEART_FIBRILLATION,
        HEART_ARREST,
        HEART_RESTARTED,
        DRESSING_REOPENED,
        TENSION_PNEUMOTHORAX,
        FRACTURE_WORSENED,
        PAIN_SHOCK,
        /** Рана заразилась (второй этап). */
        WOUND_INFECTED,
        /** Началась реакция на несовместимую кровь. */
        TRANSFUSION_REACTION
    }

    public final EnumSet<Event> events = EnumSet.noneOf(Event.class);

    public boolean has(Event e) {
        return events.contains(e);
    }

    void add(Event e) {
        events.add(e);
    }
}
