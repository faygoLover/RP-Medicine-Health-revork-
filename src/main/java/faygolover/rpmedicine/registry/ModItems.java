package faygolover.rpmedicine.registry;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.item.GmScannerItem;
import faygolover.rpmedicine.item.MedicalContainerItem;
import faygolover.rpmedicine.item.MedicalItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Предметы первого этапа (п. 6.2 ТЗ). Пока только из креатива. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpMedicine.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpMedicine.MODID);

    private static final List<RegistryObject<? extends Item>> ALL = new ArrayList<>();

    public static final RegistryObject<Item> BANDAGE = medical("bandage", 16);
    public static final RegistryObject<Item> PRESSURE_DRESSING = medical("pressure_dressing", 8);
    public static final RegistryObject<Item> HEMOSTATIC_GAUZE = medical("hemostatic_gauze", 8);
    public static final RegistryObject<Item> TOURNIQUET = medical("tourniquet", 4);
    public static final RegistryObject<Item> ESMARCH = medical("esmarch", 8);
    public static final RegistryObject<Item> SPLINT = medical("splint", 4);
    public static final RegistryObject<Item> OCCLUSIVE_DRESSING = medical("occlusive_dressing", 8);
    public static final RegistryObject<Item> DECOMPRESSION_NEEDLE = medical("decompression_needle", 8);
    public static final RegistryObject<Item> PAINKILLERS = medical("painkillers", 16);
    public static final RegistryObject<Item> MORPHINE = medical("morphine", 8);
    public static final RegistryObject<Item> ADRENALINE = medical("adrenaline", 8);
    public static final RegistryObject<Item> TXA = medical("txa", 8);
    public static final RegistryObject<Item> FIELD_SURGERY_KIT = medical("field_surgery_kit", 1);
    public static final RegistryObject<Item> SALINE = medical("saline", 4);
    public static final RegistryObject<Item> AMMONIA = medical("ammonia", 16);
    public static final RegistryObject<Item> AIRWAY = medical("airway", 8);
    public static final RegistryObject<Item> AMBU_BAG = tool("ambu_bag", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DEFIBRILLATOR = tool("defibrillator", () -> new MedicalItem(new Item.Properties().durability(10)));
    public static final RegistryObject<Item> PULSE_OXIMETER = tool("pulse_oximeter", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TONOMETER = tool("tonometer", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MEDICAL_POUCH = tool("medical_pouch", () -> new MedicalContainerItem(new Item.Properties().stacksTo(1), 6));
    public static final RegistryObject<Item> FIRST_AID_KIT = tool("first_aid_kit", () -> new MedicalContainerItem(new Item.Properties().stacksTo(1), 15));
    public static final RegistryObject<Item> GM_SCANNER = tool("gm_scanner", () -> new GmScannerItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpmedicine"))
            .icon(() -> new ItemStack(BANDAGE.get()))
            .displayItems((params, out) -> {
                for (RegistryObject<? extends Item> ro : ALL) out.accept(ro.get());
            })
            .build());

    private static RegistryObject<Item> medical(String name, int stack) {
        return tool(name, () -> new MedicalItem(new Item.Properties().stacksTo(stack)));
    }

    private static RegistryObject<Item> tool(String name, Supplier<Item> sup) {
        RegistryObject<Item> ro = ITEMS.register(name, sup);
        ALL.add(ro);
        return ro;
    }

    private ModItems() {}
}
