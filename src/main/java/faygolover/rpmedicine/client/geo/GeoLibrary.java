package faygolover.rpmedicine.client.geo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.rpmedicine.RpMedicine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 3D-модели предметов: {@code assets/rpmedicine/rpgeo/items.json} связывает предмет с моделью, текстурой
 * и анимациями. Файл и модели генерирует {@code scripts/item_art.py} из ассетов модов-референсов.
 */
public final class GeoLibrary extends SimplePreparableReloadListener<Map<ResourceLocation, GeoLibrary.Entry>> {
    public static final GeoLibrary INSTANCE = new GeoLibrary();

    /**
     * Модель предмета. {@code fit} — вписать в куб предмета по границам (модели TaCZ, у которых
     * координаты «от лица»); иначе как в GeckoLib: начало координат в центре низа блока.
     */
    public record Entry(GeoModel model, @Nullable GeoAnim anims, ResourceLocation texture, @Nullable String use,
                        @Nullable String idle, boolean fit, float size, List<String> hide, float[] bounds, List<String> hideStatic) {
        @Nullable
        public GeoAnim.Clip clip(@Nullable String name) {
            return name == null || anims == null ? null : anims.clips.get(name);
        }
    }

    private volatile Map<ResourceLocation, Entry> entries = Map.of();

    private GeoLibrary() {}

    @Nullable
    public static Entry get(ResourceLocation item) {
        return INSTANCE.entries.get(item);
    }

    /** Длина анимации применения, секунды (0 — нет). */
    public static float useSeconds(ResourceLocation item) {
        Entry e = get(item);
        GeoAnim.Clip c = e == null ? null : e.clip(e.use());
        return c == null ? 0 : c.length;
    }

    @Override
    protected Map<ResourceLocation, Entry> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, Entry> out = new HashMap<>();
        Optional<Resource> index = rm.getResource(new ResourceLocation(RpMedicine.MODID, "rpgeo/items.json"));
        if (index.isEmpty()) return out;
        Map<String, GeoModel> models = new HashMap<>();
        Map<String, GeoAnim> anims = new HashMap<>();
        try (Reader r = index.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    String geo = o.get("geo").getAsString();
                    GeoModel model = models.computeIfAbsent(geo, g -> GeoModel.parse(read(rm, g)));
                    GeoAnim anim = null;
                    if (o.has("animations")) {
                        String a = o.get("animations").getAsString();
                        anim = anims.computeIfAbsent(a, x -> GeoAnim.parse(read(rm, x)));
                    }
                    List<String> hide = o.has("hide") ? o.getAsJsonArray("hide").asList().stream().map(JsonElement::getAsString).toList() : List.of();
                    // Кости, нужные только в анимации от 1-го лица (шприц в правой руке у AI-2): в слоте, на земле
                    // и от 3-го лица скрыты, и в границах вписывания их нет.
                    List<String> hideStatic = o.has("hide_static") ? o.getAsJsonArray("hide_static").asList().stream().map(JsonElement::getAsString).toList() : List.of();
                    List<String> hideAll = new java.util.ArrayList<>(hide);
                    hideAll.addAll(hideStatic);
                    out.put(new ResourceLocation(e.getKey()), new Entry(model, anim, new ResourceLocation(o.get("texture").getAsString()),
                            o.has("use") ? o.get("use").getAsString() : null, o.has("idle") ? o.get("idle").getAsString() : null,
                            o.has("fit") && o.get("fit").getAsBoolean(), o.has("size") ? o.get("size").getAsFloat() : 0.75f, hide,
                            model.restBounds(hideAll), hideStatic));
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: 3D-модель {} не загружена: {}", e.getKey(), ex.toString());
                }
            }
        } catch (Exception ex) {
            RpMedicine.LOGGER.error("RP Medicine: rpgeo/items.json не прочитан: {}", ex.toString());
        }
        return out;
    }

    private static JsonObject read(ResourceManager rm, String location) {
        try (Reader r = rm.getResourceOrThrow(new ResourceLocation(location)).openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalStateException(location + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, Entry> map, ResourceManager rm, ProfilerFiller profiler) {
        entries = Map.copyOf(map);
        RpMedicine.LOGGER.info("RP Medicine: 3D-моделей предметов: {}", map.size());
    }
}
