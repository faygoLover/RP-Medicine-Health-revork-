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
        // Самое раннее клиентское событие мода: до первой отрисовки моделей.
        ItemPoses.init();
        e.register(PANEL);
        e.register(FINISH);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent e) {
        e.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "effects", ScreenEffects::render);
        e.registerAboveAll("hud", MedicalHud::render);
    }

    @SubscribeEvent
    public static void onReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(faygolover.rpmedicine.client.geo.GeoLibrary.INSTANCE);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.BODY_STUB.get(), BodyStubRenderer::new);
        e.registerEntityRenderer(ModEntities.VOMIT.get(), faygolover.rpmedicine.client.render.VomitRenderer::new);
        e.registerBlockEntityRenderer(faygolover.rpmedicine.registry.ModBlocks.IV_STAND_BE.get(), faygolover.rpmedicine.client.render.IvStandRenderer::new);
        e.registerBlockEntityRenderer(faygolover.rpmedicine.registry.ModBlocks.HOSPITAL_STORAGE_BE.get(), faygolover.rpmedicine.client.render.HospitalGeoRenderer::new);
    }

    /** Стетоскоп в руке: трубки от ушей к руке. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            var r = e.getSkin(skin);
            if (r instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)
                pr.addLayer(new faygolover.rpmedicine.client.render.StethoscopeLayer(pr));
        }
    }

    /** Пакеты на стойке капельницы — отдельные модели (рисует IvStandRenderer). */
    @SubscribeEvent
    public static void onModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e) {
        faygolover.rpmedicine.client.render.IvStandRenderer.allModels().forEach(e::register);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            MedicalHud.registerCore();
            MenuScreens.register(ModMenus.MEDICAL_CONTAINER.get(), MedicalContainerScreen::new);
            MenuScreens.register(ModMenus.SEARCH.get(), SearchScreen::new);
            MenuScreens.register(ModMenus.MEDICAL_STORAGE.get(), faygolover.rpmedicine.client.screen.MedicalStorageScreen::new);
            // Варианты иконок по NBT: какой орган (испорченный — отдельно) и какая конечность.
            net.minecraft.client.renderer.item.ItemProperties.register(faygolover.rpmedicine.registry.ModItems.ORGAN.get(),
                    new net.minecraft.resources.ResourceLocation(RpMedicine.MODID, "organ"), (st, level, entity, seed) -> {
                        var o = faygolover.rpmedicine.item.OrganItem.organ(st);
                        if (o == null) return 0f;
                        var lvl = net.minecraft.client.Minecraft.getInstance().level;
                        if (lvl != null && faygolover.rpmedicine.item.OrganItem.spoiled(st, lvl.getGameTime())) return 0.9f;
                        return (o.ordinal() + 1) / 10f;
                    });
            // Грязные перчатки и отсыревшая маска — своя иконка (замечание 09.10, И38).
            for (var it : java.util.List.of(faygolover.rpmedicine.registry.ModItems.SURGICAL_GLOVES.get(),
                    faygolover.rpmedicine.registry.ModItems.SURGICAL_MASK.get()))
                net.minecraft.client.renderer.item.ItemProperties.register(it, new net.minecraft.resources.ResourceLocation(RpMedicine.MODID, "dirty"),
                        (st, level, entity, seed) -> faygolover.rpmedicine.item.Wear.dirty(st) ? 1f : 0f);
            // Перчатки на руках (Curios, слот «руки»).
            if (faygolover.rpmedicine.integration.Integrations.curios()) faygolover.rpmedicine.integration.CuriosClient.register();
            // Испорченная кровь — своя иконка.
            net.minecraft.client.renderer.item.ItemProperties.register(faygolover.rpmedicine.registry.ModItems.BLOOD_BAG.get(),
                    new net.minecraft.resources.ResourceLocation(RpMedicine.MODID, "spoiled"), (st, level, entity, seed) -> {
                        var lvl = net.minecraft.client.Minecraft.getInstance().level;
                        return lvl != null && faygolover.rpmedicine.item.BloodBagItem.isSpoiled(st, lvl.getGameTime()) ? 1f : 0f;
                    });
            net.minecraft.client.renderer.item.ItemProperties.register(faygolover.rpmedicine.registry.ModItems.SEVERED_LIMB.get(),
                    new net.minecraft.resources.ResourceLocation(RpMedicine.MODID, "part"), (st, level, entity, seed) -> {
                        var p = faygolover.rpmedicine.item.SeveredLimbItem.part(st);
                        if (p == null) return 0f;
                        return switch (p.kind) {
                            case ARM -> 0.1f;
                            case LEG -> 0.2f;
                            case FOOT -> 0.3f;
                            default -> 0f;
                        };
                    });
        });
        // Кнопка «Настройки» в списке модов.
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new faygolover.rpmedicine.client.screen.ClientSettingsScreen(parent)));
    }
}
