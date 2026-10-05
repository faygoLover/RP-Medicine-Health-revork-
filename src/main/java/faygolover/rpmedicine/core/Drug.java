package faygolover.rpmedicine.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Препарат из датапака {@code drugs} (ТЗ второго этапа, п. 6): форма, эффекты дозы, предел доз за
 * окно времени и эффекты передозировки. Без Minecraft — описание читает слой датапака.
 *
 * @param doseLimit         сколько доз можно за окно (0 — без предела)
 * @param doseWindowSeconds окно, секунды в сети
 * @param overdoseArrestChance шанс остановки дыхания при передозировке
 * @param opioid            опиат: вместе с седацией угнетает дыхание, снимается налоксоном
 */
public record Drug(String id, Form form, List<Dose> effects, int doseLimit, double doseWindowSeconds, List<Dose> overdose,
                   double overdoseArrestChance, boolean opioid, Special special, Substance substance, double substanceAmount) implements Treatments.Extra {

    public Drug(String id, Form form, List<Dose> effects, int doseLimit, double doseWindowSeconds, List<Dose> overdose,
                double overdoseArrestChance, boolean opioid, Special special) {
        this(id, form, effects, doseLimit, doseWindowSeconds, overdose, overdoseArrestChance, opioid, special,
                opioid ? Substance.OPIOID : null, opioid ? 1.0 : 0);
    }


    /** Форма: таблетки (только в сознании), укол, капельница (пациент на месте), наружно (на часть тела). */
    public enum Form {
        PILL, INJECTION, DRIP, TOPICAL;

        public static Optional<Form> byId(String id) {
            String s = id.toLowerCase(Locale.ROOT);
            for (Form f : values()) if (f.name().toLowerCase(Locale.ROOT).equals(s)) return Optional.of(f);
            return Optional.empty();
        }
    }

    /** Особое действие, кроме эффектов. */
    public enum Special {
        NONE, OPIOID_ANTIDOTE, ANTISEPTIC, ANTIBIOTIC_OINTMENT,
        /** Глюкоза: сахар крови сразу выше (третий этап, п. 8). */
        GLUCOSE;

        public static Optional<Special> byId(String id) {
            String s = id.toLowerCase(Locale.ROOT);
            for (Special f : values()) if (f.name().toLowerCase(Locale.ROOT).equals(s)) return Optional.of(f);
            return Optional.empty();
        }
    }

    /** Эффект дозы: сила, задержка до начала и длительность, секунды. */
    public record Dose(DrugEffect effect, double strength, double delay, double seconds) {}
}
