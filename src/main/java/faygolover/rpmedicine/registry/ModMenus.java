package faygolover.rpmedicine.registry;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.menu.MedicalContainerMenu;
import faygolover.rpmedicine.menu.SearchMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Меню: подсумок и обыск. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RpMedicine.MODID);

    public static final RegistryObject<MenuType<MedicalContainerMenu>> MEDICAL_CONTAINER = MENUS.register("medical_container",
            () -> IForgeMenuType.create(MedicalContainerMenu::fromNetwork));
    public static final RegistryObject<MenuType<SearchMenu>> SEARCH = MENUS.register("search",
            () -> IForgeMenuType.create(SearchMenu::fromNetwork));

    public static final RegistryObject<MenuType<faygolover.rpmedicine.menu.MedicalStorageMenu>> MEDICAL_STORAGE = MENUS.register("medical_storage",
            () -> IForgeMenuType.create(faygolover.rpmedicine.menu.MedicalStorageMenu::fromNetwork));

    private ModMenus() {}
}
