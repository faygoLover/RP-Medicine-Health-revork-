package faygolover.rpmedicine.integration;

import com.google.gson.JsonObject;
import faygolover.rpcore.api.NutritionProvider;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.Metabolism;
import faygolover.rpmedicine.server.Medical;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Внутривенное питание (замечания 37 и 41 живого теста). Добавки во флаконах (глюкоза, аминокислоты, жировая
 * эмульсия, витамины) набирают шприцем и вводят в пакет физраствора на стойке; капает {@code ivAdditiveDripSeconds}
 * (20 минут), и по мере того, как капает, состав уходит моду питания (RP Culinary через RP Core) мимо желудка.
 * После — плохой аппетит. Сам физраствор поит. Без мода питания добавки ничего не дают (кроме сахара диабетику).
 * Состав на дозу — в датапаке препаратов: {@code "nutrition": {"kcal": 40, "protein": 0, "fat": 0, "carbs": 10, "vitamins": 0}}.
 */
public final class IvNutrition {
    private IvNutrition() {}

    /** Состав одной дозы добавки. */
    public record Portion(double kcal, double protein, double fat, double carbs, double vitamins) {}

    private static volatile Map<String, Portion> table = Map.of();
    private static final Map<String, Portion> LOADING = new HashMap<>();

    /** Загрузка датапака препаратов: начать заново. */
    public static synchronized void beginReload() {
        LOADING.clear();
    }

    /** Препарат с полем {@code nutrition}. */
    public static synchronized void read(String drugId, JsonObject drug) {
        if (!drug.has("nutrition")) return;
        JsonObject n = GsonHelper.getAsJsonObject(drug, "nutrition");
        double protein = GsonHelper.getAsDouble(n, "protein", 0), fat = GsonHelper.getAsDouble(n, "fat", 0),
                carbs = GsonHelper.getAsDouble(n, "carbs", 0);
        double kcal = GsonHelper.getAsDouble(n, "kcal", protein * 4 + fat * 9 + carbs * 4);
        LOADING.put(drugId, new Portion(kcal, protein, fat, carbs, GsonHelper.getAsDouble(n, "vitamins", 0)));
    }

    public static synchronized void endReload() {
        table = Map.copyOf(LOADING);
    }

    public static boolean isNutrition(String drugId) {
        return drugId != null && table.containsKey(drugId);
    }

    /** Капнуло {@code doses} доз добавки: моду питания (за минуту игры — остальное капельница уже растянула). */
    public static void deliver(LivingEntity patient, MedicalState m, String drugId, double doses) {
        Portion p = table.get(drugId);
        if (p == null || doses <= 0) return;
        if (p.carbs() > 0 && patient instanceof Player pl && Medical.traits(pl).diabetic)
            Metabolism.eatCarbs(m, p.carbs() * doses, MedicalSettings.get());
        NutritionProvider n = CoreNutrition.provider();
        if (n == null || !(patient instanceof Player pl)) return;
        n.feedIntravenous(pl, p.kcal() * doses, p.protein() * doses, p.fat() * doses, p.carbs() * doses, p.vitamins() * doses, 60);
    }

    /** Влито {@code ml} раствора (физраствор, кровь, добавки) — вода моду питания. */
    public static void water(LivingEntity patient, double ml) {
        NutritionProvider n = CoreNutrition.provider();
        if (n == null || ml <= 0 || !(patient instanceof Player pl)) return;
        n.addWater(pl, ml / 1000.0 * MedicalSettings.get().ivWaterPercentPerLiter);
    }
}
