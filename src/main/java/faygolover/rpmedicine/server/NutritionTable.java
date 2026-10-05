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
    /** Вид еды (−1 — не считается). */
    private static final Map<Item, Integer> CATEGORY = new HashMap<>();
    @Nullable
    private static Map<Item, List<Recipe<?>>> byResult;
    @Nullable
    private static RegistryAccess access;

    public static synchronized void invalidate() {
        CACHE.clear();
        CATEGORY.clear();
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
        NutritionRules.Entry entry = NutritionRules.entry(item);
        if (entry != null) {
            CACHE.put(item, entry.food());
            CATEGORY.put(item, entry.category());
            return entry.food();
        }
        if (depth >= MAX_DEPTH || !path.add(item)) return null;
        Nutrition.Food found = null;
        List<Recipe<?>> recipes = byResult != null ? byResult.getOrDefault(item, List.of()) : List.of();
        int foundCat = -1;
        for (Recipe<?> r : recipes) {
            Nutrition.Food sum = Nutrition.Food.ZERO;
            boolean any = false;
            double[] byCat = new double[Nutrition.CATEGORIES.length];
            for (Ingredient ing : r.getIngredients()) {
                Item src = ingredientItem(ing, depth, path);
                if (src == null) continue;
                Nutrition.Food part = CACHE.get(src);
                if (part == null) continue;
                sum = sum.plus(part);
                any = true;
                int c = CATEGORY.getOrDefault(src, -1);
                if (c >= 0) byCat[c] += Math.max(1, part.kcal());
            }
            if (!any || sum.isEmpty()) continue;
            int count = Math.max(1, r.getResultItem(access).getCount());
            found = sum.scale(1.0 / count);
            // Вид блюда — вид ингредиента с наибольшими калориями.
            double best = 0;
            for (int c = 0; c < byCat.length; c++) if (byCat[c] > best) {
                best = byCat[c];
                foundCat = c;
            }
            break;
        }
        path.remove(item);
        if (found == null) {
            found = estimate(item);
            FoodProperties fp = found != null ? item.getFoodProperties(new ItemStack(item), null) : null;
            foundCat = fp != null && fp.isMeat() ? Nutrition.category("meat") : -1;
        }
        // Промежуточные «нет» не кэшируем: на другом пути цикл может разрешиться.
        if (found != null) {
            CACHE.put(item, found);
            CATEGORY.put(item, foundCat);
        }
        return found;
    }

    /** Ингредиент рецепта: первый вариант, у которого известен состав. */
    @Nullable
    private static Item ingredientItem(Ingredient ing, int depth, Set<Item> path) {
        for (ItemStack st : ing.getItems()) {
            if (resolve(st.getItem(), depth + 1, path) != null) return st.getItem();
        }
        return null;
    }

    /** Вид еды предмета (−1 — не считается). */
    public static synchronized int category(MinecraftServer server, Item item) {
        if (get(server, item) == null) return -1;
        return CATEGORY.getOrDefault(item, -1);
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

    /** Виды еды для клиента (только известные). */
    public static Map<ResourceLocation, Integer> categories(MinecraftServer server) {
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (!item.isEdible() && NutritionRules.base(item) == null) continue;
            int c = category(server, item);
            if (c >= 0) out.put(ForgeRegistries.ITEMS.getKey(item), c);
        }
        return out;
    }
}
