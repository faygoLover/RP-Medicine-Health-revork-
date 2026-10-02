package faygolover.rpmedicine.client;

import com.mojang.blaze3d.platform.InputConstants;
import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.client.render.BodyStubRenderer;
import faygolover.rpmedicine.client.screen.MedicalContainerScreen;
import faygolover.rpmedicine.client.screen.SearchScreen;
import faygolover.rpmedicine.registry.ModEntities;
import faygolover.rpmedicine.registry.ModMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Регистрация клиентского: клавиши, слои HUD, экраны меню, отрисовка заглушки. */
@Mod.EventBusSubscriber(modid = RpMedicine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    private static final String CATEGORY = "key.categories.rpmedicine";

    /** Панель осмотра: себя или игрока, на которого смотришь. Лежачему — меню лежачего. */
    public static final KeyMapping PANEL = new KeyMapping("key.rpmedicine.panel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    /** Добивание — удерживать. */
    public static final KeyMapping FINISH = new KeyMapping("key.rpmedicine.finish", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) {
        e.register(PANEL);
        e.register(FINISH);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent e) {
        e.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "effects", ScreenEffects::render);
        e.registerAboveAll("hud", MedicalHud::render);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.BODY_STUB.get(), BodyStubRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MEDICAL_CONTAINER.get(), MedicalContainerScreen::new);
            MenuScreens.register(ModMenus.SEARCH.get(), SearchScreen::new);
        });
    }
}
