package faygolover.rpmedicine.server;

import faygolover.rpmedicine.core.Nutrition;
import faygolover.rpmedicine.data.NutritionRules;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Состав любой еды (по образцу Nutritional Balance, но количественно): базовые ингредиенты — из датапака;
 * блюдо — сумма ингредиентов рецепта, которым оно получается, делённая на число получаемых штук (жарка и
 * варка состав сохраняют). Рекурсия по рецептам до {@link #MAX_DEPTH}, циклы отсекаются. Еда без рецепта и
 * без записи — оценка по сытости предмета (мясо — белок и жир, остальное — углеводы). Результаты кэшируются
 * до перезагрузки датапаков.
 */
public final class NutritionTable {
    private NutritionTable() {}

    private static final int MAX_DEPTH = 6;
    private static final Map<Item, Nutrition.Food> CACHE = new HashMap<>();
    @Nullable
    private static Map<Item, List<Recipe<?>>> byResult;
    @Nullable
    private static RegistryAccess access;

    public static synchronized void invalidate() {
        CACHE.clear();
        byResult = null;
    }

    private static synchronized void index(MinecraftServer server) {
        if (byResult != null) return;
        access = server.registryAccess();
        byResult = new HashMap<>();
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            ItemStack out;
            try {
                out = r.getResultItem(access);
            } catch (Exception e) {
                continue;
            }
            if (out == null || out.isEmpty() || r.getIngredients().isEmpty()) continue;
            byResult.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
        }
        // Детерминированно: по id рецепта.
        for (List<Recipe<?>> l : byResult.values()) l.sort((a, b) -> a.getId().compareTo(b.getId()));
    }

    /** Состав порции предмета или null (не еда и не ингредиент). */
    @Nullable
    public static synchronized Nutrition.Food get(MinecraftServer server, Item item) {
        index(server);
        return resolve(item, 0, new HashSet<>());
    }

    @Nullable
    private static Nutrition.Food resolve(Item item, int depth, Set<Item> path) {
        if (CACHE.containsKey(item)) return CACHE.get(item);
        Nutrition.Food base = NutritionRules.base(item);
        if (base != null) {
            CACHE.put(item, base);
            return base;
        }
        if (depth >= MAX_DEPTH || !path.add(item)) return null;
        Nutrition.Food found = null;
        List<Recipe<?>> recipes = byResult != null ? byResult.getOrDefault(item, List.of()) : List.of();
        for (Recipe<?> r : recipes) {
            Nutrition.Food sum = Nutrition.Food.ZERO;
            boolean any = false;
            for (Ingredient ing : r.getIngredients()) {
                Nutrition.Food part = ingredient(ing, depth, path);
                if (part != null) {
                    sum = sum.plus(part);
                    any = true;
                }
            }
            if (!any || sum.isEmpty()) continue;
            int count = Math.max(1, r.getResultItem(access).getCount());
            found = sum.scale(1.0 / count);
            break;
        }
        path.remove(item);
        if (found == null) found = estimate(item);
        // Промежуточные «нет» не кэшируем: на другом пути цикл может разрешиться.
        if (found != null) CACHE.put(item, found);
        return found;
    }

    /** Состав ингредиента рецепта: первый вариант, у которого он известен. */
    @Nullable
    private static Nutrition.Food ingredient(Ingredient ing, int depth, Set<Item> path) {
        for (ItemStack st : ing.getItems()) {
            Nutrition.Food f = resolve(st.getItem(), depth + 1, path);
            if (f != null) return f;
        }
        return null;
    }

    /** Еда без рецепта и записи — по сытости: ~90 ккал за единицу голода. */
    @Nullable
    private static Nutrition.Food estimate(Item item) {
        FoodProperties fp = item.getFoodProperties(new ItemStack(item), null);
        if (fp == null) return null;
        double kcal = fp.getNutrition() * 90.0;
        if (fp.isMeat()) return new Nutrition.Food(kcal, kcal * 0.45 / 4, kcal * 0.55 / 9, 0, 1);
        return new Nutrition.Food(kcal, kcal * 0.08 / 4, kcal * 0.12 / 9, kcal * 0.8 / 4, 4);
    }

    /** Таблица для клиента (подсказки): вся еда и известные ингредиенты. */
    public static Map<ResourceLocation, Nutrition.Food> snapshot(MinecraftServer server) {
        Map<ResourceLocation, Nutrition.Food> out = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            boolean food = item.isEdible() || NutritionRules.base(item) != null;
            if (!food) continue;
            Nutrition.Food f = get(server, item);
            if (f != null && !f.isEmpty()) out.put(ForgeRegistries.ITEMS.getKey(item), f);
        }
        return out;
    }
}
