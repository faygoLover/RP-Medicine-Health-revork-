package faygolover.rpmedicine.server;

import faygolover.rpcore.api.NutritionProvider;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.StepInput;
import faygolover.rpmedicine.integration.CoreNutrition;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.integration.lso.LsoCompat;
import net.minecraft.server.level.ServerPlayer;

/**
 * Голод, жажда, температура среды. Голод и жажду с 0.3.0 ведёт RP Culinary (через RP Core,
 * {@link CoreNutrition}): сытость в калориях, вода, нутриенты, вес. Без него — ванильная сытость и вода LSO
 * (если стоит). Температура среды: с LSO — его, лихорадка поднимает его температуру; без LSO — простые правила.
 */
public final class SurvivalService {
    private SurvivalService() {}

    /** Шаг физиологии: вода, сытость, среда во входные данные. */
    public static void prepareStep(ServerPlayer sp, MedicalData d, StepInput in, double sprintSeconds) {
        MedicalSettings s = MedicalSettings.get();
        MedicalState m = d.state;
        NutritionProvider n = CoreNutrition.provider();
        if (CoreNutrition.pull(sp, d) && n != null) {
            in.satiety = Math.min(1, n.satiety(sp));
            in.hydration = n.handles(faygolover.rpcore.api.RpIds.FOOD_THIRST) ? m.thirst / 100.0 : 1.0;
        } else {
            in.satiety = sp.getFoodData().getFoodLevel() / 20.0;
            double h = Integrations.lso() ? LsoCompat.hydration(sp) : -1;
            in.hydration = h < 0 ? 1 : h;
            m.thirst = in.hydration * 100;
        }
        // Сон в кровати поддерживает иммунитет (второй этап, п. 5.2).
        if (sp.isSleeping()) in.immunityFactor *= s.immunitySleepFactor;
        // Перки RP Perks через черты Core: «Живучий»/«Хрупкий» — заживление, «Турист»/«Домашний» — иммунитет.
        in.healFactor *= faygolover.rpmedicine.integration.CoreCompat.healingFactor(sp, s);
        in.immunityFactor *= faygolover.rpmedicine.integration.CoreCompat.immunityFactor(sp, s);
        boolean survival = !sp.isCreative() && !sp.isSpectator();
        if (Integrations.lso()) {
            in.ambientTempShift = LsoCompat.temperatureOffset(sp) * s.lsoTempScale;
            // Лихорадка поднимает температуру LSO; модификатор обновляется, только когда заметно изменился.
            double fever = Math.max(0, m.bodyTemp - s.normalBodyTemp - 0.3) * s.feverToLso;
            if (Double.isNaN(d.lastLsoFever) || Math.abs(fever - d.lastLsoFever) > 0.25 || (fever == 0 && d.lastLsoFever != 0)) {
                LsoCompat.setFever(sp, fever);
                d.lastLsoFever = fever;
            }
        } else {
            in.ambientTempShift = survival ? ambient(sp, s) : 0;
        }
    }

    /** Простые правила среды без LSO: холодный и жаркий биом, рыхлый снег. */
    static double ambient(ServerPlayer sp, MedicalSettings s) {
        if (sp.isInPowderSnow || sp.wasInPowderSnow) return s.powderSnowTempShift;
        float t = sp.level().getBiome(sp.blockPosition()).value().getBaseTemperature();
        if (t <= 0.15f) return s.coldBiomeTempShift;
        if (t >= 1.5f) return s.hotBiomeTempShift;
        return 0;
    }

    /** Команда food add: голод и жажда за {@code hours} часов (с RP Culinary — его прокрутка). */
    public static void advance(ServerPlayer sp, double hours) {
        NutritionProvider n = CoreNutrition.provider();
        if (n != null) {
            n.advance(sp, hours);
            MedicalData d = Medical.data(sp);
            if (d != null) CoreNutrition.pull(sp, d);
            Medical.changed(sp);
            return;
        }
        MedicalSettings s = MedicalSettings.get();
        var food = sp.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - (int) Math.round(s.foodLossPerHour * hours)));
        food.setSaturation(0);
        if (Integrations.lso()) LsoCompat.loseThirst(sp, s.lsoThirstLossPerHour * hours);
        Medical.changed(sp);
    }

    /** Офлайн (тело): запомнить, сколько часов прокрутить при входе. */
    public static void advanceOffline(MedicalState m, double hours) {
        m.pendingFoodHours += hours;
    }

    /** Вход: прокрутить накопленное командой food add, пока игрок был офлайн. */
    public static void onLogin(ServerPlayer sp) {
        MedicalState m = Medical.state(sp);
        if (m == null || m.pendingFoodHours <= 0) return;
        double hours = m.pendingFoodHours;
        m.pendingFoodHours = 0;
        advance(sp, hours);
    }
}
