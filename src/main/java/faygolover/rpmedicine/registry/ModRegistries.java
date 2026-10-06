package faygolover.rpmedicine.registry;

import net.minecraftforge.eventbus.api.IEventBus;

/** Регистрация всего, что мод добавляет в реестры: предметы, сущности, звуки, меню. */
public final class ModRegistries {
    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        ModSounds.SOUNDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
    }
}
