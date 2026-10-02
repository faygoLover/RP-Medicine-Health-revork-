package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.core.Chance;
import faygolover.rpmedicine.core.InjuryProfile;
import faygolover.rpmedicine.core.WoundType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Датапак {@code data/<ns>/rpmedicine/damage_sources/*.json}: тип урона → тип раны, множитель
 * тяжести, шансы осложнений, как выбирать часть тела (п. 11 ТЗ).
 */
public final class DamageRules extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final DamageRules INSTANCE = new DamageRules();

    /** Правила, к которым обращаются по id (пули TaCZ). */
    public static final ResourceLocation GUN_PENETRATION = new ResourceLocation(RpMedicine.MODID, "gun/penetration");
    public static final ResourceLocation GUN_PLATE = new ResourceLocation(RpMedicine.MODID, "gun/plate");
    public static final ResourceLocation GUN_PLATE_HEAVY = new ResourceLocation(RpMedicine.MODID, "gun/plate_heavy");
    public static final ResourceLocation GUN_RICOCHET = new ResourceLocation(RpMedicine.MODID, "gun/ricochet");
    public static final ResourceLocation GUN_HELMET = new ResourceLocation(RpMedicine.MODID, "gun/helmet");
    public static final ResourceLocation DEFAULT = new ResourceLocation(RpMedicine.MODID, "default");

    public static final class Rule {
        public final ResourceLocation id;
        public final int priority;
        public final Matcher<DamageType> damageTypes;
        public final Matcher<Item> attackerItems;
        public final Matcher<EntityType<?>> attackerEntities;
        public final InjuryProfile profile;

        Rule(ResourceLocation id, int priority, Matcher<DamageType> types, Matcher<Item> items, Matcher<EntityType<?>> entities, InjuryProfile profile) {
            this.id = id;
            this.priority = priority;
            this.damageTypes = types;
            this.attackerItems = items;
            this.attackerEntities = entities;
            this.profile = profile;
        }

        boolean matches(DamageSource src) {
            if (damageTypes.isEmpty() || !damageTypes.matches(src.typeHolder())) return false;
            Entity attacker = src.getEntity();
            if (!attackerItems.isEmpty()) {
                if (!(attacker instanceof LivingEntity le)) return false;
                ItemStack held = le.getMainHandItem();
                if (held.isEmpty() || !attackerItems.matches(held.getItemHolder())) return false;
            }
            if (!attackerEntities.isEmpty()) {
                if (attacker == null) return false;
                if (!attackerEntities.matches(attacker.getType().builtInRegistryHolder())) return false;
            }
            return true;
        }
    }

    private volatile List<Rule> sorted = List.of();
    private volatile Map<ResourceLocation, Rule> byId = Map.of();
    private final InjuryProfile fallback;

    private DamageRules() {
        super(GSON, "rpmedicine/damage_sources");
        fallback = new InjuryProfile("fallback", WoundType.BRUISE, InjuryProfile.Location.HIT_POINT);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
        List<Rule> list = new ArrayList<>();
        Map<ResourceLocation, Rule> ids = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
            try {
                Rule r = parse(e.getKey(), e.getValue().getAsJsonObject());
                list.add(r);
                ids.put(r.id, r);
            } catch (Exception ex) {
                RpMedicine.LOGGER.error("RP Medicine: ошибка в damage_sources {}: {}", e.getKey(), ex.getMessage());
            }
        }
        list.sort(Comparator.comparingInt((Rule r) -> -r.priority).thenComparing(r -> r.id.toString()));
        sorted = List.copyOf(list);
        byId = Map.copyOf(ids);
        RpMedicine.LOGGER.info("RP Medicine: загружено правил урона: {}", list.size());
    }

    static Rule parse(ResourceLocation id, JsonObject o) {
        InjuryProfile p = new InjuryProfile();
        p.id = id.toString();
        String wound = GsonHelper.getAsString(o, "wound", "bruise");
        p.wound = wound.equalsIgnoreCase("none") ? null
                : WoundType.byId(wound).orElseThrow(() -> new IllegalArgumentException("неизвестный тип раны " + wound));
        p.location = InjuryProfile.Location.valueOf(GsonHelper.getAsString(o, "location", "hit_point").toUpperCase(Locale.ROOT));
        if (p.wound == null) p.location = InjuryProfile.Location.NONE;
        p.severityMultiplier = GsonHelper.getAsDouble(o, "severity_multiplier", 1.0);
        p.highFallDamage = GsonHelper.getAsDouble(o, "high_fall_damage", 10.0);
        JsonObject c = GsonHelper.getAsJsonObject(o, "complications", new JsonObject());
        p.fracture = chance(c, "fracture");
        p.openFractureFraction = GsonHelper.getAsDouble(c, "open_fracture_fraction", 0.0);
        p.arterial = chance(c, "arterial");
        p.internal = chance(c, "internal");
        p.foreignBody = chance(c, "foreign_body");
        if (c.has("foreign_body") && c.get("foreign_body").isJsonObject()) {
            JsonObject fb = c.getAsJsonObject("foreign_body");
            p.foreignBodyMin = GsonHelper.getAsInt(fb, "count_min", 1);
            p.foreignBodyMax = Math.max(p.foreignBodyMin, GsonHelper.getAsInt(fb, "count_max", p.foreignBodyMin));
        }
        p.concussion = chance(c, "concussion");
        if (c.has("concussion") && c.get("concussion").isJsonObject())
            p.concussionPerSeverity = GsonHelper.getAsDouble(c.getAsJsonObject("concussion"), "amount_per_severity", 2.0);
        p.pneumothorax = chance(c, "pneumothorax");
        JsonObject ph = GsonHelper.getAsJsonObject(o, "physiology", new JsonObject());
        p.spo2PerDamage = GsonHelper.getAsDouble(ph, "spo2_per_damage", 0);
        p.brainPerDamage = GsonHelper.getAsDouble(ph, "brain_per_damage", 0);
        p.bloodPerDamage = GsonHelper.getAsDouble(ph, "blood_per_damage", 0);
        return new Rule(id, GsonHelper.getAsInt(o, "priority", 0),
                Matcher.parse(o.get("damage_types"), Registries.DAMAGE_TYPE),
                Matcher.parse(o.get("attacker_items"), Registries.ITEM),
                Matcher.parse(o.get("attacker_entities"), Registries.ENTITY_TYPE), p);
    }

    private static Chance chance(JsonObject c, String key) {
        if (!c.has(key) || !c.get(key).isJsonObject()) return Chance.NEVER;
        JsonObject o = c.getAsJsonObject(key);
        return new Chance(GsonHelper.getAsDouble(o, "min_severity", 0), GsonHelper.getAsDouble(o, "per_severity", 0),
                GsonHelper.getAsDouble(o, "max", 1));
    }

    /** Правило для источника урона: первое подходящее по приоритету, иначе правило по умолчанию. */
    public InjuryProfile profileFor(DamageSource src) {
        for (Rule r : sorted) if (r.matches(src)) return r.profile;
        return byId(DEFAULT);
    }

    public InjuryProfile byId(ResourceLocation id) {
        Rule r = byId.get(id);
        if (r != null) return r.profile;
        Rule d = byId.get(DEFAULT);
        return d != null ? d.profile : fallback;
    }

    public int size() {
        return sorted.size();
    }
}
