package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.Substance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Датапак {@code rpmedicine/substances} (ТЗ третьего этапа, п. 9.1): чужие предметы и эффекты — дозы
 * веществ. Мод не знает эти моды в коде.
 * <pre>{"substance": "alcohol", "amount": 1.0,
 *  "items": ["vinery:red_wine"],          // допил (use finish)
 *  "right_click": ["the_dirty_stuff:cigarette"],  // ПКМ предметом (курение без «допивания»)
 *  "effects": ["tobacconistmod:nicotine"],        // наложенный эффект
 *  "cooldown_seconds": 20}</pre>
 */
public final class SubstanceRules {
    private SubstanceRules() {}

    public record Entry(Substance substance, double amount, double cooldownSeconds) {}

    private static volatile Map<ResourceLocation, Entry> byItem = Map.of();
    private static volatile Map<ResourceLocation, Entry> byClick = Map.of();
    private static volatile Map<ResourceLocation, Entry> byEffect = Map.of();

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(new Gson(), "rpmedicine/substances") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Entry> items = new HashMap<>();
            Map<ResourceLocation, Entry> clicks = new HashMap<>();
            Map<ResourceLocation, Entry> effects = new HashMap<>();
            for (var f : files.entrySet()) {
                try {
                    JsonObject o = f.getValue().getAsJsonObject();
                    String sid = GsonHelper.getAsString(o, "substance");
                    Substance sub = Substance.byId(sid).orElseThrow(() -> new IllegalArgumentException("неизвестное вещество " + sid));
                    Entry e = new Entry(sub, GsonHelper.getAsDouble(o, "amount", 1.0), GsonHelper.getAsDouble(o, "cooldown_seconds", 0));
                    put(items, o, "items", e);
                    put(clicks, o, "right_click", e);
                    put(effects, o, "effects", e);
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: вещество {}: {}", f.getKey(), ex.getMessage());
                }
            }
            byItem = Map.copyOf(items);
            byClick = Map.copyOf(clicks);
            byEffect = Map.copyOf(effects);
            RpMedicine.LOGGER.info("RP Medicine: веществ — предметов {}, по ПКМ {}, эффектов {}", items.size(), clicks.size(), effects.size());
        }
    };

    private static void put(Map<ResourceLocation, Entry> map, JsonObject o, String key, Entry e) {
        for (JsonElement el : GsonHelper.getAsJsonArray(o, key, new JsonArray())) {
            ResourceLocation rl = ResourceLocation.tryParse(el.getAsString());
            if (rl != null) map.put(rl, e);
        }
    }

    @Nullable
    public static Entry forItem(@Nullable ResourceLocation id) {
        return id == null ? null : byItem.get(id);
    }

    @Nullable
    public static Entry forClick(@Nullable ResourceLocation id) {
        return id == null ? null : byClick.get(id);
    }

    @Nullable
    public static Entry forEffect(@Nullable ResourceLocation id) {
        return id == null ? null : byEffect.get(id);
    }
}
