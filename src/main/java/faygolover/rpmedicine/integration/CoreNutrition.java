package faygolover.rpmedicine.integration;

import faygolover.rpcore.api.NutritionProvider;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.RpIds;
import faygolover.rpcore.api.event.FoodEatenEvent;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Metabolism;
import faygolover.rpmedicine.core.Nutrition;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.server.level.ServerPlayer;

/**
 * Питание из мода питания (RP Culinary) через RP Core. Перед каждым шагом физиологии Medicine копирует к себе
 * воду, нутриенты, аппетит, вес и жир; если Medicine сама их поменяла (ГМ выставил вес, медкарта, рвота, жажда
 * от высокого сахара) — сначала отдаёт разницу моду питания. Без мода питания ничего не делает.
 */
public final class CoreNutrition {
    private CoreNutrition() {}

    /** Мод питания или null. */
    public static NutritionProvider provider() {
        return RpCoreAPI.nutrition();
    }

    /** Взять у мода питания (и отдать ему свои изменения). false — мода питания нет. */
    public static boolean pull(ServerPlayer sp, MedicalData d) {
        NutritionProvider n = provider();
        MedicalState m = d.state;
        if (n == null) {
            m.nutritionKnown = false;
            m.poorAppetite = false;
            return false;
        }
        MedicalSettings s = MedicalSettings.get();
        if (n.handles(RpIds.FOOD_WEIGHT)) {
            boolean changedHere = !Double.isNaN(d.nutritionWeight)
                    && (Math.abs(m.weightKg - d.nutritionWeight) > 1e-6 || Math.abs(m.bodyFat - d.nutritionFat) > 1e-6);
            if (changedHere) n.setBody(sp, m.weightKg, m.bodyFat);
            double w = n.weightKg(sp);
            // Кровь — доля от нормы по весу: похудел — крови меньше, но это не кровопотеря.
            if (Math.abs(w - m.weightKg) > 1e-6) {
                double frac = m.bloodFraction(s);
                m.weightKg = w;
                m.bloodVolume = m.normalBlood(s) * frac;
            }
            m.bodyFat = n.bodyFatPercent(sp);
            d.nutritionWeight = m.weightKg;
            d.nutritionFat = m.bodyFat;
        }
        if (n.handles(RpIds.FOOD_THIRST)) {
            if (!Double.isNaN(d.nutritionThirst) && m.thirst < d.nutritionThirst - 1e-6) n.addWater(sp, m.thirst - d.nutritionThirst);
            m.thirst = n.hydration(sp) * 100;
            d.nutritionThirst = m.thirst;
        } else {
            m.thirst = 100;
            d.nutritionThirst = Double.NaN;
        }
        boolean known = true;
        for (int i = 0; i < Nutrition.COUNT; i++) {
            double v = n.nutrient(sp, Nutrition.IDS[i]);
            if (Double.isNaN(v)) known = false;
            else m.nutrients[i] = v;
        }
        m.nutritionKnown = known;
        m.poorAppetite = n.poorAppetite(sp);
        return true;
    }

    /** Рвота: желудок пустеет, вода уходит. false — мода питания нет. */
    public static boolean vomit(ServerPlayer sp, double waterPercent) {
        NutritionProvider n = provider();
        if (n == null) return false;
        n.vomit(sp);
        n.addWater(sp, -waterPercent);
        MedicalData d = Medical.data(sp);
        if (d != null) d.nutritionThirst = n.hydration(sp) * 100;
        return true;
    }

    /** Съел (событие RP Culinary): у диабетика углеводы поднимают сахар. */
    public static void onEaten(FoodEatenEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.carbs() <= 0 || !Medical.traits(sp).diabetic) return;
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        Metabolism.eatCarbs(m, e.carbs(), MedicalSettings.get());
        Medical.changed(sp);
    }
}
