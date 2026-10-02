package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.TreatmentAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Датапаки {@code rpmedicine/items} (свойства наших предметов: время, действие, минимальный уровень)
 * и {@code rpmedicine/item_aliases} (чужой предмет → наш предмет).
 */
public final class ItemRules {
    private static final Gson GSON = new GsonBuilder().create();

    /** Свойства медицинского предмета. */
    public record Spec(ResourceLocation item, TreatmentAction action, double seconds, int minLevel, boolean consume) {}

    private static volatile Map<ResourceLocation, Spec> specs = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> aliases = Map.of();

    public static final SimpleJsonResourceReloadListener ITEMS = new SimpleJsonResourceReloadListener(GSON, "rpmedicine/items") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Spec> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    ResourceLocation item = new ResourceLocation(GsonHelper.getAsString(o, "item"));
                    String actionId = GsonHelper.getAsString(o, "action");
                    TreatmentAction action = TreatmentAction.byId(actionId)
                            .orElseThrow(() -> new IllegalArgumentException("неизвестное действие " + actionId));
                    out.put(item, new Spec(item, action, GsonHelper.getAsDouble(o, "seconds", 2.0),
                            GsonHelper.getAsInt(o, "min_level", 0), GsonHelper.getAsBoolean(o, "consume", true)));
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: ошибка в items {}: {}", e.getKey(), ex.getMessage());
                }
            }
            specs = Map.copyOf(out);
            RpMedicine.LOGGER.info("RP Medicine: загружено медицинских предметов: {}", out.size());
        }
    };

    public static final SimpleJsonResourceReloadListener ALIASES = new SimpleJsonResourceReloadListener(GSON, "rpmedicine/item_aliases") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, ResourceLocation> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = GsonHelper.getAsJsonObject(e.getValue().getAsJsonObject(), "aliases");
                    for (Map.Entry<String, JsonElement> a : o.entrySet()) {
                        ResourceLocation from = ResourceLocation.tryParse(a.getKey());
                        ResourceLocation to = ResourceLocation.tryParse(a.getValue().getAsString());
                        if (from != null && to != null) out.put(from, to);
                    }
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: ошибка в item_aliases {}: {}", e.getKey(), ex.getMessage());
                }
            }
            aliases = Map.copyOf(out);
            RpMedicine.LOGGER.info("RP Medicine: загружено аналогов предметов: {}", out.size());
        }
    };

    private ItemRules() {}

    /** Свойства предмета с учётом аналогов; null — предмет не медицинский. */
    @Nullable
    public static Spec specFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        Spec s = specs.get(id);
        if (s != null) return s;
        ResourceLocation alias = aliases.get(id);
        if (alias != null && specs.get(alias) != null) return specs.get(alias);
        // Препараты из датапака drugs (второй этап).
        DrugRules.Entry d = DrugRules.forItem(id);
        if (d == null && alias != null) d = DrugRules.forItem(alias);
        if (d != null) {
            TreatmentAction a = d.drug().form() == faygolover.rpmedicine.core.Drug.Form.TOPICAL ? TreatmentAction.DRUG_TOPICAL : TreatmentAction.DRUG;
            return new Spec(id, a, d.seconds(), d.minLevel(), true);
        }
        return null;
    }

    /** Препарат предмета (с учётом аналогов) или null. */
    @Nullable
    public static faygolover.rpmedicine.core.Drug drugFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        DrugRules.Entry d = DrugRules.forItem(id);
        if (d == null && id != null && aliases.get(id) != null) d = DrugRules.forItem(aliases.get(id));
        return d != null ? d.drug() : null;
    }

    public static boolean isMedical(ItemStack stack) {
        return specFor(stack) != null;
    }

    public static Map<ResourceLocation, Spec> all() {
        return specs;
    }
}
