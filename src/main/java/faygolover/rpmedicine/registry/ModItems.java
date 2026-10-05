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
    public static final RegistryObject<Item> STABILIZATION_KIT = medical("stabilization_kit", 4);
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
    // Второй этап
    public static final RegistryObject<Item> EMPTY_BLOOD_BAG = medical("empty_blood_bag", 16);
    public static final RegistryObject<Item> BLOOD_BAG = tool("blood_bag", () -> new faygolover.rpmedicine.item.BloodBagItem(new Item.Properties().stacksTo(1)));
    // Препараты (датапак drugs)
    public static final RegistryObject<Item> PARACETAMOL = medical("paracetamol", 16);
    public static final RegistryObject<Item> IBUPROFEN = medical("ibuprofen", 16);
    public static final RegistryObject<Item> KETOROLAC = medical("ketorolac", 8);
    public static final RegistryObject<Item> TRAMADOL = medical("tramadol", 16);
    public static final RegistryObject<Item> NALOXONE = medical("naloxone", 8);
    public static final RegistryObject<Item> AMOXICILLIN = medical("amoxicillin", 16);
    public static final RegistryObject<Item> CEFTRIAXONE = medical("ceftriaxone", 8);
    public static final RegistryObject<Item> DIAZEPAM = medical("diazepam", 8);
    public static final RegistryObject<Item> NOREPINEPHRINE = medical("norepinephrine", 4);
    public static final RegistryObject<Item> ATROPINE = medical("atropine", 8);
    public static final RegistryObject<Item> ANTISEPTIC = tool("antiseptic", () -> new MedicalItem(new Item.Properties().durability(20)));
    public static final RegistryObject<Item> ANTIBIOTIC_OINTMENT = tool("antibiotic_ointment", () -> new MedicalItem(new Item.Properties().durability(10)));
    // Диагностика
    public static final RegistryObject<Item> STETHOSCOPE = tool("stethoscope", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> THERMOMETER = tool("thermometer", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PORTABLE_SCANNER = tool("portable_scanner", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HEMOANALYZER = tool("hemoanalyzer", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LANCET = medical("lancet", 32);
    public static final RegistryObject<Item> TEST_TUBE = medical("test_tube", 16);
    /** Пустой шприц: с ним медик (4+) сам выбирает дозу препарата для укола или капельницы. */
    public static final RegistryObject<Item> SYRINGE = medical("syringe", 16);
    public static final RegistryObject<Item> BLOOD_DRAW_SYRINGE = medical("blood_draw_syringe", 16);
    public static final RegistryObject<Item> BLOOD_SAMPLE = tool("blood_sample", () -> new faygolover.rpmedicine.item.BloodSampleItem(new Item.Properties().stacksTo(1)));
    // Пули, швы (второй этап, п. 8)
    public static final RegistryObject<Item> SURGICAL_TWEEZERS = tool("surgical_tweezers", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SUTURE_KIT = tool("suture_kit", () -> new MedicalItem(new Item.Properties().durability(5)));
    public static final RegistryObject<Item> SCISSORS = tool("scissors", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    // Третий этап: анестезия (п. 3)
    public static final RegistryObject<Item> LIDOCAINE = medical("lidocaine", 8);
    public static final RegistryObject<Item> KETAMINE = medical("ketamine", 8);
    public static final RegistryObject<Item> PROPOFOL = medical("propofol", 8);
    // Хирургия (третий этап, п. 4.4)
    public static final RegistryObject<Item> SCALPEL = tool("scalpel", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HEMOSTAT = tool("hemostat", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RETRACTOR = tool("retractor", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SURGICAL_DRILL = tool("surgical_drill", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> OSTEOSYNTHESIS_KIT = medical("osteosynthesis_kit", 8);
    public static final RegistryObject<Item> VASCULAR_SUTURE = medical("vascular_suture", 16);
    public static final RegistryObject<Item> CHEST_DRAIN = medical("chest_drain", 8);
    public static final RegistryObject<Item> SURGICAL_MASK = tool("surgical_mask", () -> new faygolover.rpmedicine.item.SurgicalMaskItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SURGICAL_GLOVES = medical("surgical_gloves", 1);
    // Ампутация и протезы (третий этап, п. 6)
    public static final RegistryObject<Item> BONE_SAW = tool("bone_saw", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PROSTHETIC_FOOT = tool("prosthetic_foot", () -> new faygolover.rpmedicine.item.ProstheticItem(
            faygolover.rpmedicine.core.BodyPartState.Prosthesis.FOOT, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PEG_LEG = tool("peg_leg", () -> new faygolover.rpmedicine.item.ProstheticItem(
            faygolover.rpmedicine.core.BodyPartState.Prosthesis.PEG_LEG, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PROSTHETIC_HOOK = tool("prosthetic_hook", () -> new faygolover.rpmedicine.item.ProstheticItem(
            faygolover.rpmedicine.core.BodyPartState.Prosthesis.HOOK, new Item.Properties().stacksTo(1)));
    // Органы вне тела (п. 7)
    public static final RegistryObject<Item> ORGAN_CONTAINER = medical("organ_container", 4);
    public static final RegistryObject<Item> ORGAN = tool("organ", () -> new faygolover.rpmedicine.item.OrganItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CYCLOSPORINE = medical("cyclosporine", 16);
    // Диабет (п. 8)
    public static final RegistryObject<Item> INSULIN = medical("insulin", 8);
    public static final RegistryObject<Item> GLUCOSE_TABLETS = medical("glucose_tablets", 16);
    public static final RegistryObject<Item> GLUCOMETER = tool("glucometer", () -> new faygolover.rpmedicine.item.MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SEVERED_LIMB = tool("severed_limb", () -> new faygolover.rpmedicine.item.SeveredLimbItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LARYNGOSCOPE = tool("laryngoscope", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ENDOTRACHEAL_TUBE = medical("endotracheal_tube", 8);
    public static final RegistryObject<Item> MEDCARD = tool("medcard", () -> new faygolover.rpmedicine.item.MedcardItem(new Item.Properties().stacksTo(16)));
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
