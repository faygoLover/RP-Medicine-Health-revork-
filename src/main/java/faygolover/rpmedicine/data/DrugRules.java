package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.Drug;
import faygolover.rpmedicine.core.DrugEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Датапак {@code rpmedicine/drugs} (ТЗ второго этапа, п. 6): препараты и их предметы.
 * <pre>{
 *   "items": ["rpmedicine:paracetamol"], "form": "pill", "seconds": 2, "min_level": 0,
 *   "effects": [{"effect": "analgesia", "strength": 12, "delay": 60, "duration": 1800}],
 *   "dose": {"limit": 4, "window_hours": 24},
 *   "overdose": {"effects": [...], "arrest_chance": 0.1},
 *   "opioid": false, "special": "none"
 * }</pre>
 */
public final class DrugRules {
    private static final Gson GSON = new GsonBuilder().create();

    /** Препарат и свойства его предмета. */
    public record Entry(Drug drug, double seconds, int minLevel) {}

    private static volatile Map<ResourceLocation, Entry> byItem = Map.of();
    private static volatile Map<String, Drug> byId = Map.of();

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(GSON, "rpmedicine/drugs") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Entry> items = new HashMap<>();
            Map<String, Drug> drugs = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Drug d = parse(e.getKey().toString(), o);
                    double seconds = GsonHelper.getAsDouble(o, "seconds", defaultSeconds(d.form()));
                    Entry entry = new Entry(d, seconds, GsonHelper.getAsInt(o, "min_level", 0));
                    drugs.put(d.id(), d);
                    for (JsonElement it : GsonHelper.getAsJsonArray(o, "items")) {
                        ResourceLocation rl = ResourceLocation.tryParse(it.getAsString());
                        if (rl != null) items.put(rl, entry);
                    }
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: ошибка в drugs {}: {}", e.getKey(), ex.getMessage());
                }
            }
            byItem = Map.copyOf(items);
            byId = Map.copyOf(drugs);
            RpMedicine.LOGGER.info("RP Medicine: загружено препаратов: {}", drugs.size());
        }
    };

    private DrugRules() {}

    /** Время применения по форме, если в файле не задано (п. 6.1: укол 2 с, капельница 10 с). */
    static double defaultSeconds(Drug.Form f) {
        return switch (f) {
            case PILL, INJECTION -> 2;
            case DRIP -> 10;
            case TOPICAL -> 3;
        };
    }

    static Drug parse(String id, JsonObject o) {
        String formId = GsonHelper.getAsString(o, "form");
        Drug.Form form = Drug.Form.byId(formId).orElseThrow(() -> new IllegalArgumentException("неизвестная форма " + formId));
        List<Drug.Dose> effects = doses(GsonHelper.getAsJsonArray(o, "effects", new JsonArray()));
        int limit = 0;
        double window = 0;
        if (o.has("dose")) {
            JsonObject d = GsonHelper.getAsJsonObject(o, "dose");
            limit = GsonHelper.getAsInt(d, "limit", 0);
            window = GsonHelper.getAsDouble(d, "window_hours", 0) * 3600.0;
        }
        List<Drug.Dose> overdose = List.of();
        double arrest = 0;
        if (o.has("overdose")) {
            JsonObject od = GsonHelper.getAsJsonObject(o, "overdose");
            overdose = doses(GsonHelper.getAsJsonArray(od, "effects", new JsonArray()));
            arrest = GsonHelper.getAsDouble(od, "arrest_chance", 0);
        }
        String sp = GsonHelper.getAsString(o, "special", "none");
        Drug.Special special = Drug.Special.byId(sp).orElseThrow(() -> new IllegalArgumentException("неизвестное действие " + sp));
        boolean opioid = GsonHelper.getAsBoolean(o, "opioid", false);
        faygolover.rpmedicine.core.Substance sub = opioid ? faygolover.rpmedicine.core.Substance.OPIOID : null;
        double subAmount = opioid ? 1.0 : 0;
        if (o.has("substance")) {
            JsonObject so = GsonHelper.getAsJsonObject(o, "substance");
            String sid = GsonHelper.getAsString(so, "id");
            sub = faygolover.rpmedicine.core.Substance.byId(sid).orElseThrow(() -> new IllegalArgumentException("неизвестное вещество " + sid));
            subAmount = GsonHelper.getAsDouble(so, "amount", 1.0);
        }
        return new Drug(id, form, effects, limit, window, overdose, arrest, opioid, special, sub, subAmount);
    }

    private static List<Drug.Dose> doses(JsonArray arr) {
        List<Drug.Dose> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject e = el.getAsJsonObject();
            String eid = GsonHelper.getAsString(e, "effect");
            DrugEffect effect = DrugEffect.byId(eid).orElseThrow(() -> new IllegalArgumentException("неизвестный эффект " + eid));
            out.add(new Drug.Dose(effect, GsonHelper.getAsDouble(e, "strength"), GsonHelper.getAsDouble(e, "delay", 0),
                    GsonHelper.getAsDouble(e, "duration")));
        }
        return out;
    }

    @Nullable
    public static Entry forItem(@Nullable ResourceLocation item) {
        return item == null ? null : byItem.get(item);
    }

    /** Все id препаратов по алфавиту (для панели ГМа). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(byId.keySet());
        java.util.Collections.sort(out);
        return out;
    }

    @Nullable
    public static Drug byId(String id) {
        return byId.get(id);
    }
}
