package faygolover.rpmedicine.integration;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

/**
 * Мягкие интеграции (п. 9 ТЗ): код для каждого мода в отдельных классах, которые загружаются
 * только при наличии мода. Здесь — только проверки и вызовы через флаги.
 */
public final class Integrations {
    private Integrations() {}

    private static boolean tacz;
    private static boolean zeroContact;
    private static boolean stamina;
    private static boolean curios;
    private static boolean carryOn;
    private static boolean voicechat;

    public static void init() {
        ModList ml = ModList.get();
        tacz = ml.isLoaded("tacz");
        zeroContact = ml.isLoaded("zerocontact");
        stamina = ml.isLoaded("rpstamina");
        curios = ml.isLoaded("curios");
        carryOn = ml.isLoaded("carryon");
        voicechat = ml.isLoaded("voicechat");
        if (tacz) MinecraftForge.EVENT_BUS.register(faygolover.rpmedicine.integration.tacz.GunHits.class);
        RpMedicine.LOGGER.info("RP Medicine: интеграции — TaCZ {}, Zero Contact {}, RP Stamina {}, Curios {}, Carry On {}, Simple Voice Chat {}",
                tacz, zeroContact, stamina, curios, carryOn, voicechat);
    }

    /** Предупредить о модах, с которыми RP Medicine конфликтует (п. 9 ТЗ). */
    public static void warnConflicts() {
        if (!ServerConfig.WARN_CONFLICTS.get()) return;
        ModList ml = ModList.get();
        for (String id : new String[]{"tacmed", "playerrevive"}) {
            if (ml.isLoaded(id)) RpMedicine.LOGGER.warn("RP Medicine: установлен мод {} — он конфликтует с RP Medicine (нокдаун, лечение). Его нужно удалить.", id);
        }
        if (ml.isLoaded("legendarysurvivaloverhaul"))
            RpMedicine.LOGGER.warn("RP Medicine: установлен LSO — выключите в его конфиге локальный урон по частям тела, health overhaul и множитель хедшота.");
        if (zeroContact)
            RpMedicine.LOGGER.info("RP Medicine: Zero Contact — его выносливость нужно выключить (своя у RP Stamina). Бронежилет Zero Contact пока защищает и конечности (ждём событие от разработчика).");
    }

    public static boolean tacz() {
        return tacz;
    }

    public static boolean zeroContact() {
        return zeroContact;
    }

    public static boolean stamina() {
        return stamina;
    }

    public static boolean curios() {
        return curios;
    }

    public static boolean carryOn() {
        return carryOn;
    }

    public static boolean voicechat() {
        return voicechat;
    }

    /** Урон от пули TaCZ по пациенту — обрабатывает интеграция. */
    public static boolean handleGunDamage(LivingEntity target, DamageSource src, float amount) {
        return tacz && faygolover.rpmedicine.integration.tacz.GunHits.onDamage(target, src, amount);
    }

    public static void endServerTick() {
        if (tacz) faygolover.rpmedicine.integration.tacz.GunHits.flush();
    }

    public static void serverStopped() {
        if (tacz) faygolover.rpmedicine.integration.tacz.GunHits.clear();
    }

    /** Расход выносливости RP Stamina (переноска тела). */
    public static void consumeStamina(ServerPlayer sp, float amount) {
        if (stamina) StaminaCompat.consume(sp, amount);
    }
}
