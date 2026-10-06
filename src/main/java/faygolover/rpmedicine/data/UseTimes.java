package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import faygolover.rpmedicine.RpMedicine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Минимальное время применения предмета, секунды — длина анимации его 3D-модели: действие не может
 * кончиться раньше, чем отыграет анимация. Датапак {@code rpmedicine/use_times}:
 * <pre>{"times": {"rpmedicine:bandage": 1.75}}</pre> Файлы генерирует {@code scripts/item_art.py}.
 */
public final class UseTimes {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, Double> times = Map.of();

    private UseTimes() {}

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(GSON, "rpmedicine/use_times") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Double> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    for (Map.Entry<String, JsonElement> t : e.getValue().getAsJsonObject().getAsJsonObject("times").entrySet())
                        out.merge(new ResourceLocation(t.getKey()), t.getValue().getAsDouble(), Math::max);
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: ошибка в use_times {}: {}", e.getKey(), ex.getMessage());
                }
            }
            times = Map.copyOf(out);
        }
    };

    /** Минимум секунд для предмета (0 — без ограничения). */
    public static double min(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? 0 : times.getOrDefault(id, 0.0);
    }

    /** Для тестов. */
    public static void set(Map<ResourceLocation, Double> map) {
        times = Map.copyOf(map);
    }
}
