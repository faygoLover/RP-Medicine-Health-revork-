package faygolover.rpmedicine.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.capability.MedicalData;
import faygolover.rpmedicine.core.MedicalSettings;
import faygolover.rpmedicine.core.MedicalState;
import faygolover.rpmedicine.core.StepInput;
import faygolover.rpmedicine.integration.Integrations;
import faygolover.rpmedicine.integration.lso.LsoCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Голод, жажда, температура среды (ТЗ второго этапа, п. 12). Без LSO — свои простые правила: жажда
 * убывает со временем и восполняется напитками из датапака {@code rpmedicine/drinks}, холодные и жаркие
 * биомы сдвигают температуру тела. С LSO — вода и температура берутся из него, лихорадка поднимает его
 * температуру. Баров, фляг и сезонов нет.
 */
public final class SurvivalService {
    private SurvivalService() {}

    private static final Gson GSON = new GsonBuilder().create();

    /** Напиток: сколько воды (0–100) и, для зелий, какое зелье. */
    record Drink(double amount, String potion) {}

    private static volatile Map<ResourceLocation, Drink> drinks = Map.of();

    public static final SimpleJsonResourceReloadListener DRINKS = new SimpleJsonResourceReloadListener(GSON, "rpmedicine/drinks") {
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, Drink> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Drink d = new Drink(GsonHelper.getAsDouble(o, "amount"), GsonHelper.getAsString(o, "potion", ""));
                    for (JsonElement it : GsonHelper.getAsJsonArray(o, "items")) {
                        ResourceLocation rl = ResourceLocation.tryParse(it.getAsString());
                        if (rl != null) out.put(rl, d);
                    }
                } catch (Exception ex) {
                    RpMedicine.LOGGER.error("RP Medicine: ошибка в drinks {}: {}", e.getKey(), ex.getMessage());
                }
            }
            drinks = Map.copyOf(out);
        }
    };

    /** Шаг физиологии: вода, сытость, среда во входные данные; своя жажда убывает. */
    public static void prepareStep(ServerPlayer sp, MedicalData d, StepInput in, double sprintSeconds) {
        MedicalSettings s = MedicalSettings.get();
        MedicalState m = d.state;
        in.satiety = sp.getFoodData().getFoodLevel() / 20.0;
        boolean survival = !sp.isCreative() && !sp.isSpectator();
        if (Integrations.lso()) {
            double h = LsoCompat.hydration(sp);
            in.hydration = h < 0 ? 1 : h;
            in.ambientTempShift = LsoCompat.temperatureOffset(sp) * s.lsoTempScale;
            // Лихорадка поднимает температуру LSO; модификатор обновляется, только когда заметно изменился.
            double fever = Math.max(0, m.bodyTemp - s.normalBodyTemp - 0.3) * s.feverToLso;
            if (Math.abs(fever - d.lastLsoFever) > 0.25 || (fever == 0 && d.lastLsoFever != 0)) {
                LsoCompat.setFever(sp, fever);
                d.lastLsoFever = fever;
            }
        } else {
            if (s.ownThirstEnabled && survival) {
                double loss = s.thirstLossPerHour * (in.dt + sprintSeconds * (s.thirstSprintFactor - 1)) / 3600.0;
                m.thirst = Math.max(0, m.thirst - loss);
            }
            in.hydration = s.ownThirstEnabled ? m.thirst / 100.0 : 1.0;
            in.ambientTempShift = survival ? ambient(sp, s) : 0;
        }
    }

    /** Простые правила среды без LSO: холодный и жаркий биом, рыхлый снег. */
    static double ambient(ServerPlayer sp, MedicalSettings s) {
        if (sp.isInPowderSnow || sp.wasInPowderSnow) return s.powderSnowTempShift;
        float t = sp.level().getBiome(sp.blockPosition()).value().getBaseTemperature();
        if (t <= 0.15f) return s.coldBiomeTempShift;
        if (t >= 1.5f) return s.hotBiomeTempShift;
        return 0;
    }

    /** Выпил: своя жажда восполняется (с LSO воду считает он). */
    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || Integrations.lso() || !MedicalSettings.get().ownThirstEnabled) return;
        ItemStack used = e.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(used.getItem());
        Drink d = id != null ? drinks.get(id) : null;
        if (d == null) return;
        if (!d.potion().isEmpty()) {
            ResourceLocation p = ForgeRegistries.POTIONS.getKey(PotionUtils.getPotion(used));
            if (p == null || !p.toString().equals(d.potion())) return;
        }
        MedicalState m = Medical.state(sp);
        if (m == null) return;
        m.thirst = Math.min(100, m.thirst + d.amount());
        Medical.changed(sp);
    }

    /** Команда food add: голод и жажда за {@code hours} часов. */
    public static void advance(ServerPlayer sp, double hours) {
        MedicalSettings s = MedicalSettings.get();
        var food = sp.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - (int) Math.round(s.foodLossPerHour * hours)));
        food.setSaturation(0);
        MedicalState m = Medical.state(sp);
        if (Integrations.lso()) LsoCompat.loseThirst(sp, s.lsoThirstLossPerHour * hours);
        else if (m != null && s.ownThirstEnabled) m.thirst = Math.max(0, m.thirst - s.thirstLossPerHour * hours);
        Medical.changed(sp);
    }

    /** Офлайн (тело): запомнить, сколько снять при входе. */
    public static void advanceOffline(MedicalState m, double hours) {
        MedicalSettings s = MedicalSettings.get();
        m.pendingFoodLoss += s.foodLossPerHour * hours;
        m.pendingThirstLoss += s.thirstLossPerHour * hours;
    }

    /** Вход: снять накопленное командой food add, пока игрок был офлайн. */
    public static void onLogin(ServerPlayer sp) {
        MedicalState m = Medical.state(sp);
        if (m == null || (m.pendingFoodLoss <= 0 && m.pendingThirstLoss <= 0)) return;
        MedicalSettings s = MedicalSettings.get();
        double hours = s.foodLossPerHour > 0 ? m.pendingFoodLoss / s.foodLossPerHour
                : s.thirstLossPerHour > 0 ? m.pendingThirstLoss / s.thirstLossPerHour : 0;
        m.pendingFoodLoss = 0;
        m.pendingThirstLoss = 0;
        if (hours > 0) advance(sp, hours);
    }
}
