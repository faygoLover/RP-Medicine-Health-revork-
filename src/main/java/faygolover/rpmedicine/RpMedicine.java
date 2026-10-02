package faygolover.rpmedicine;

import com.mojang.logging.LogUtils;
import faygolover.rpmedicine.capability.MedicalCapability;
import faygolover.rpmedicine.config.ClientConfig;
import faygolover.rpmedicine.config.ServerConfig;
import faygolover.rpmedicine.network.Network;
import faygolover.rpmedicine.registry.ModRegistries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * RP Medicine: детальная система здоровья и медицины для ролевого сервера.
 * Все расчёты на сервере; клиент получает только изменения и только то, что ему положено видеть.
 */
@Mod(RpMedicine.MODID)
public final class RpMedicine {
    public static final String MODID = "rpmedicine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpMedicine() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        ModRegistries.register(modBus);
        modBus.addListener(MedicalCapability::register);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onConfig);
        modBus.addListener(this::onConfigReload);

        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.entity.Entity.class, MedicalCapability::attach);
        ModSetup.registerForgeListeners(MinecraftForge.EVENT_BUS);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Network.register();
            ModSetup.commonSetup();
        });
    }

    private void onConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) ServerConfig.apply();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) ServerConfig.apply();
    }
}
