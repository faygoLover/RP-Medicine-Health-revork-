package faygolover.rpmedicine.core;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Препарат в крови (решения, п. 1.16). Доза сначала попадает в «депо» (мышца, желудок) и всасывается,
 * из крови выводится по полувыведению. Сила эффекта — от уровня в крови в стандартных дозах (с поправкой
 * на вес): около 1 — обычное действие, выше — сильнее, с порога — передозировка. Срок действия получается сам.
 */
public final class DrugLevels {
    private DrugLevels() {}

    /** Путь введения. */
    public enum Route {
        /** Укол в мышцу (шприц-ручка): всасывание за 1–2 минуты. */
        IM,
        /** Шприц в вену или в порт катетера: сразу. */
        IV,
        /** Таблетки: всасывание за 1–2 минуты. */
        ORAL,
        /** Капельница: столько, сколько накапало. */
        DRIP
    }

    /** Препарат в теле. drug — описание (после загрузки находится по id). */
    public static final class Level {
        public final String id;
        public Drug drug;
        /** Ещё не всосалось, стандартные дозы. */
        public double depot;
        /** В крови, стандартные дозы. */
        public double plasma;
        /** Время всасывания депо (постоянная, с). */
        public double absorbSeconds;
        /** Передозировка уже случилась на этом подъёме (шанс остановки дыхания — один раз). */
        public boolean overdosed;

        public Level(String id) {
            this.id = id;
        }

        public Level copy() {
            Level l = new Level(id);
            l.drug = drug;
            l.depot = depot;
            l.plasma = plasma;
            l.absorbSeconds = absorbSeconds;
            l.overdosed = overdosed;
            return l;
        }
    }

    /** Найти препарат по id (ставит слой датапака). */
    public static Function<String, Drug> resolver = id -> null;

    /** Ввести дозу (в стандартных дозах, до поправки на вес). */
    public static void give(MedicalState m, Drug d, double doses, Route route, MedicalSettings s) {
        double eff = Drugs.effectiveDose(m, d, doses, s);
        Level l = m.drugLevels.computeIfAbsent(d.id(), Level::new);
        l.drug = d;
        switch (route) {
            case IV, DRIP -> l.plasma += eff;
            case IM -> {
                l.depot += eff;
                l.absorbSeconds = s.drugAbsorbImSeconds;
            }
            case ORAL -> {
                l.depot += eff;
                l.absorbSeconds = s.drugAbsorbOralSeconds;
            }
        }
    }

    /** Путь по форме препарата, если не задан. */
    public static Route routeOf(Drug d) {
        return switch (d.form()) {
            case PILL -> Route.ORAL;
            case DRIP -> Route.DRIP;
            default -> Route.IM;
        };
    }

    /** Уровень в крови, стандартные дозы. */
    public static double plasma(MedicalState m, String id) {
        Level l = m.drugLevels.get(id);
        return l == null ? 0 : l.plasma;
    }

    /** Шаг: всасывание, выведение, эффекты. */
    public static void tick(MedicalState m, double dt, MedicalSettings s, RandomGenerator rnd) {
        m.pkEffects.clear();
        if (m.drugLevels.isEmpty()) return;
        double opioidLeft = 0;
        Iterator<Map.Entry<String, Level>> it = m.drugLevels.entrySet().iterator();
        while (it.hasNext()) {
            Level l = it.next().getValue();
            if (l.drug == null) l.drug = resolver.apply(l.id);
            Drug d = l.drug;
            if (d == null) {
                it.remove();
                continue;
            }
            if (l.depot > 0) {
                double moved = l.depot * (1 - Math.exp(-dt / Math.max(1, l.absorbSeconds)));
                if (l.depot < 0.005) moved = l.depot;
                l.depot -= moved;
                l.plasma += moved;
            }
            // Толерантность (опиаты и т. п.) — выводится быстрее.
            double half = d.halfLife() * Substances.durationFactor(m, d.substance());
            l.plasma *= Math.pow(0.5, dt / Math.max(1, half));
            double min = d.kinetics().minLevel();
            if (l.plasma < min * 0.25 && l.depot <= 0) {
                it.remove();
                continue;
            }
            if (l.plasma >= min) {
                for (Drug.Dose dose : d.effects()) {
                    if (dose.effect() == DrugEffect.LOCAL_ANESTHESIA) continue;
                    m.pkEffects.merge(dose.effect(), dose.strength() * response(l.plasma), Double::sum);
                }
                if (d.opioid()) opioidLeft = Math.max(opioidLeft, half * Math.log(l.plasma / min) / Math.log(2));
            }
            boolean over = l.plasma > d.overdoseLevel();
            if (over) {
                for (Drug.Dose dose : d.overdose()) m.pkEffects.merge(dose.effect(), dose.strength(), Double::sum);
                if (!l.overdosed && rnd.nextDouble() < d.overdoseArrestChance()) m.respiratoryArrest = true;
                l.overdosed = true;
            } else if (l.plasma < d.overdoseLevel() * 0.8) {
                l.overdosed = false;
            }
        }
        if (opioidLeft > 0) m.opioidSeconds = Math.max(m.opioidSeconds, opioidLeft);
    }

    /**
     * Сила от уровня (модель Emax): 1 доза — 1, меньше — слабее, больше — сильнее, но с насыщением
     * (2 дозы — 1,2; дальше рост медленный, опасность — от эффектов передозировки).
     */
    public static double response(double level) {
        return level * (1 + EMAX_K) / (level + EMAX_K);
    }

    private static final double EMAX_K = 0.5;

    /** Пересчитать эффекты сразу (после укола в вену, антидота). */
    public static void refresh(MedicalState m, MedicalSettings s, RandomGenerator rnd) {
        tick(m, 0, s, rnd);
    }

    /** Антидот: выбросить препарат из крови (налоксон — опиаты). */
    public static void clearOpioids(MedicalState m) {
        m.drugLevels.values().removeIf(l -> l.drug != null && l.drug.opioid());
    }
}
