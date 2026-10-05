package faygolover.rpmedicine.hospital;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.data.Matcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Датапак {@code rpmedicine/hospital_blocks}: какие чужие блоки выполняют функции госпиталя.
 * <pre>{"function": "bed", "blocks": ["industrialhellscape:medical_bed", "#mypack:beds"], "radius": 3}</pre>
 * Файлы с одной функцией складываются; радиус берётся наибольший. Клиент получает тот же список
 * при входе и после {@code /reload} (нужен, чтобы знать, что игрок смотрит на монитор).
 */
public final class HospitalBlocks {
    private static final Gson GSON = new GsonBuilder().create();

    /** Сырые списки (для отправки клиенту) и радиусы. */
    private static volatile Map<HospitalFunction, List<String>> entries = Map.of();
    private static volatile Map<HospitalFunction, Integer> radii = Map.of();
    private static volatile Map<HospitalFunction, Matcher<Block>> matchers = Map.of();

    public static final SimpleJsonResourceReloadListener LOADER = new SimpleJsonResourceReloadListener(GSON, "rpmedicine/hospital_blocks") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            EnumMap<HospitalFunction, List<String>> out = new EnumMap<>(HospitalFunction.class);
            EnumMap<HospitalFunction, Integer> rad = new EnumMap<>(HospitalFunction.class);
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    String fid = GsonHelper.getAsString(o, "function");
                    HospitalFunction f = HospitalFunction.byId(fid)
                            .orElseThrow(() -> new IllegalArgumentException("неизвестная функция " + fid));
                    List<String> list = out.computeIfAbsent(f, k -> new ArrayList<>());
                    for (JsonElement b : GsonHelper.getAsJsonArray(o, "blocks")) list.add(b.getAsString().trim());
                    int r = GsonHelper.getAsInt(o, "radius", f.defaultRadius);
                    rad.merge(f, Math.max(0, Math.min(16, r)), Math::max);
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: ошибка в hospital_blocks {}: {}", e.getKey(), ex.getMessage());
                }
            }
            set(out, rad);
            RpMedicine.LOGGER.info("RP Medicine: загружено функций госпиталя: {}", out.size());
        }
    };

    private HospitalBlocks() {}

    /** Подменяет списки целиком (загрузка датапака, пакет с сервера, тесты). */
    public static void set(Map<HospitalFunction, List<String>> blocks, Map<HospitalFunction, Integer> radius) {
        EnumMap<HospitalFunction, List<String>> e = new EnumMap<>(HospitalFunction.class);
        EnumMap<HospitalFunction, Integer> r = new EnumMap<>(HospitalFunction.class);
        EnumMap<HospitalFunction, Matcher<Block>> m = new EnumMap<>(HospitalFunction.class);
        for (HospitalFunction f : HospitalFunction.VALUES) {
            List<String> list = List.copyOf(blocks.getOrDefault(f, List.of()));
            e.put(f, list);
            r.put(f, radius.getOrDefault(f, f.defaultRadius));
            m.put(f, Matcher.of(list, Registries.BLOCK));
        }
        entries = e;
        radii = r;
        matchers = m;
    }

    public static Map<HospitalFunction, List<String>> entries() {
        return entries;
    }

    public static Map<HospitalFunction, Integer> radii() {
        return radii;
    }

    public static int radius(HospitalFunction f) {
        return radii.getOrDefault(f, f.defaultRadius);
    }

    public static boolean is(BlockState state, HospitalFunction f) {
        Matcher<Block> m = matchers.get(f);
        return m != null && !m.isEmpty() && m.matches(state.getBlockHolder());
    }

    /** На блоке можно лежать (койка или операционный стол). */
    public static boolean isBed(BlockState state) {
        return is(state, HospitalFunction.BED) || is(state, HospitalFunction.OPERATING_TABLE) || is(state, HospitalFunction.RESTRAINT_TABLE);
    }

    /** Ближайший блок с функцией в кубе радиусом {@code r} вокруг точки, или null. */
    @Nullable
    public static BlockPos findNearest(BlockGetter level, BlockPos center, HospitalFunction f, int r) {
        Matcher<Block> m = matchers.get(f);
        if (m == null || m.isEmpty()) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!m.matches(level.getBlockState(p).getBlockHolder())) continue;
                    double d = (double) dx * dx + dy * dy + dz * dz;
                    if (d < bestDist) {
                        bestDist = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
