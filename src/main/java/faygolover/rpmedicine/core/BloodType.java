package faygolover.rpmedicine.core;

import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Группа крови по ABO и резусу (ТЗ второго этапа, п. 4.1). Порядок (ordinal) используется в
 * сохранении и в весах распределения в конфиге — не менять.
 */
public enum BloodType {
    O_NEG("o-", "O−"), O_POS("o+", "O+"), A_NEG("a-", "A−"), A_POS("a+", "A+"),
    B_NEG("b-", "B−"), B_POS("b+", "B+"), AB_NEG("ab-", "AB−"), AB_POS("ab+", "AB+");

    public static final BloodType[] VALUES = values();

    public final String id;
    public final String label;

    BloodType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    private boolean hasA() {
        return this == A_NEG || this == A_POS || this == AB_NEG || this == AB_POS;
    }

    private boolean hasB() {
        return this == B_NEG || this == B_POS || this == AB_NEG || this == AB_POS;
    }

    public boolean rhPositive() {
        return ordinal() % 2 == 1;
    }

    /** Можно ли перелить эту кровь реципиенту: антигенов донора нет у реципиента — нельзя (O− всем, AB+ от всех). */
    public boolean canDonateTo(BloodType recipient) {
        if (hasA() && !recipient.hasA()) return false;
        if (hasB() && !recipient.hasB()) return false;
        return !rhPositive() || recipient.rhPositive();
    }

    public static Optional<BloodType> byId(String id) {
        String s = id.toLowerCase(Locale.ROOT).replace('−', '-');
        for (BloodType t : VALUES) if (t.id.equals(s)) return Optional.of(t);
        return Optional.empty();
    }

    public static BloodType byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : O_POS;
    }

    /** Случайная группа по весам (порядок как у {@link #VALUES}). */
    public static BloodType random(double[] weights, RandomGenerator rnd) {
        double sum = 0;
        for (int i = 0; i < VALUES.length && i < weights.length; i++) sum += Math.max(0, weights[i]);
        if (sum <= 0) return O_POS;
        double x = rnd.nextDouble() * sum;
        for (int i = 0; i < VALUES.length && i < weights.length; i++) {
            x -= Math.max(0, weights[i]);
            if (x < 0) return VALUES[i];
        }
        return AB_POS;
    }
}
