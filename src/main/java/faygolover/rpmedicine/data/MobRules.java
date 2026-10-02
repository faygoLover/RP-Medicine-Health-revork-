package faygolover.rpmedicine.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Датапак {@code rpmedicine/mobs}: каким мобам включены упрощённые травмы (п. 12 ТЗ). */
public final class MobRules extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final MobRules INSTANCE = new MobRules();

    public record Rule(Matcher<EntityType<?>> entities, boolean bleeding, boolean fracture, boolean painShock) {}

    private volatile List<Rule> rules = List.of();

    private MobRules() {
        super(GSON, "rpmedicine/mobs");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
        List<Rule> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
            try {
                JsonObject o = e.getValue().getAsJsonObject();
                out.add(new Rule(Matcher.parse(o.get("entities"), Registries.ENTITY_TYPE),
                        GsonHelper.getAsBoolean(o, "bleeding", true),
                        GsonHelper.getAsBoolean(o, "fracture", true),
                        GsonHelper.getAsBoolean(o, "pain_shock", true)));
            } catch (Exception ex) {
                RpMedicine.LOGGER.error("RP Medicine: ошибка в mobs {}: {}", e.getKey(), ex.getMessage());
            }
        }
        rules = List.copyOf(out);
    }

    @Nullable
    public Rule ruleFor(EntityType<?> type) {
        for (Rule r : rules) if (!r.entities().isEmpty() && r.entities().matches(type.builtInRegistryHolder())) return r;
        return null;
    }
}
