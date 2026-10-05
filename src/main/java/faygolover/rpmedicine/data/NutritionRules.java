package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.Nutrition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Датапак {@code rpmedicine/nutrition}: состав базовых ингредиентов — тех, что не крафтятся, а идут в
 * рецепты (зерно, овощи, сырое мясо, молоко). Состав готовых блюд считает {@code NutritionTable} по рецептам.
 * <pre>{"items": ["minecraft:beef", "#forge:raw_beef"], "kcal": 500, "protein": 52, "fat": 32, "carbs": 0, "vitamins": 2}</pre>
 * {@code kcal} можно не задавать — посчитается по БЖУ. Порция — один предмет.
 */
public final class NutritionRules {
    private NutritionRules() {}

    public record Entry(Matcher<Item> items, Nutrition.Food food, int priority) {}

    private static volatile List<Entry> entries = List.of();

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(new Gson(), "rpmedicine/nutrition") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            List<Entry> out = new ArrayList<>();
            for (var f : files.entrySet()) {
                try {
                    JsonObject o = f.getValue().getAsJsonObject();
                    Nutrition.Food food = new Nutrition.Food(GsonHelper.getAsDouble(o, "kcal", 0), GsonHelper.getAsDouble(o, "protein", 0),
                            GsonHelper.getAsDouble(o, "fat", 0), GsonHelper.getAsDouble(o, "carbs", 0), GsonHelper.getAsDouble(o, "vitamins", 0)).withKcal();
                    out.add(new Entry(Matcher.parse(o.get("items"), Registries.ITEM), food, GsonHelper.getAsInt(o, "priority", 0)));
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: питание {}: {}", f.getKey(), ex.getMessage());
                }
            }
            out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            entries = List.copyOf(out);
            faygolover.rpmedicine.server.NutritionTable.invalidate();
            RpMedicine.LOGGER.info("RP Medicine: базовых ингредиентов питания: {}", out.size());
        }
    };

    /** Состав базового ингредиента или null. */
    public static Nutrition.Food base(Item item) {
        var holder = item.builtInRegistryHolder();
        for (Entry e : entries) if (e.items().matches(holder)) return e.food();
        return null;
    }
}
