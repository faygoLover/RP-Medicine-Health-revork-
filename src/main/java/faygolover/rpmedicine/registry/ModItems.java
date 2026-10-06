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
    public static final RegistryObject<Item> MORPHINE = pen("morphine");
    public static final RegistryObject<Item> ADRENALINE = pen("adrenaline");
    public static final RegistryObject<Item> TXA = pen("txa");
    public static final RegistryObject<Item> STABILIZATION_KIT = medical("stabilization_kit", 4);
    public static final RegistryObject<Item> FIELD_SURGERY_KIT = medical("field_surgery_kit", 1);
    public static final RegistryObject<Item> SALINE = medical("saline", 4);
    /** Венозный катетер: через него капает со стойки (замечание 35). */
    public static final RegistryObject<Item> IV_CATHETER = medical("iv_catheter", 16);
    /** Опустевший пакет капельницы — мусор (решения, п. 1.16). */
    public static final RegistryObject<Item> USED_IV_BAG = medical("used_iv_bag", 16);
    /** Бланк анализа крови (замечание 06.10). */
    public static final RegistryObject<Item> LAB_REPORT = tool("lab_report", () -> new faygolover.rpmedicine.item.LabReportItem(new Item.Properties().stacksTo(1)));
    // Мебель госпиталя — свои блоки (решения, п. 1.16).
    public static final RegistryObject<Item> HOSPITAL_BED = block("hospital_bed", ModBlocks.HOSPITAL_BED, false);
    public static final RegistryObject<Item> OPERATING_TABLE = block("operating_table", ModBlocks.OPERATING_TABLE, false);
    public static final RegistryObject<Item> VITALS_MONITOR = block("vitals_monitor", ModBlocks.VITALS_MONITOR, false);
    public static final RegistryObject<Item> MEDICINE_CABINET = block("medicine_cabinet", ModBlocks.MEDICINE_CABINET, true);
    public static final RegistryObject<Item> MEDICINE_CRATE = block("medicine_crate", ModBlocks.MEDICINE_CRATE, true);
    public static final RegistryObject<Item> STERILIZER = block("sterilizer", ModBlocks.STERILIZER, true);
    public static final RegistryObject<Item> LAB_TABLE = block("lab_table", ModBlocks.LAB_TABLE, true);
    public static final RegistryObject<Item> THERMOSTAT = block("thermostat", ModBlocks.THERMOSTAT, false);
    public static final RegistryObject<Item> OXYGEN_TANK = block("oxygen_tank", ModBlocks.OXYGEN_TANK, false);

    private static RegistryObject<Item> block(String name, RegistryObject<net.minecraft.world.level.block.Block> b, boolean geo) {
        return tool(name, () -> geo ? new faygolover.rpmedicine.item.GeoBlockItem(b.get(), new Item.Properties())
                : new net.minecraft.world.item.BlockItem(b.get(), new Item.Properties()));
    }

    /** Стойка капельницы — свой блок. */
    public static final RegistryObject<Item> IV_STAND = tool("iv_stand", () -> new net.minecraft.world.item.BlockItem(ModBlocks.IV_STAND.get(), new Item.Properties()));
    public static final RegistryObject<Item> AMMONIA = medical("ammonia", 16);
    public static final RegistryObject<Item> AIRWAY = medical("airway", 8);
    public static final RegistryObject<Item> AMBU_BAG = tool("ambu_bag", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DEFIBRILLATOR = tool("defibrillator", () -> new MedicalItem(new Item.Properties().durability(10)));
    public static final RegistryObject<Item> PULSE_OXIMETER = tool("pulse_oximeter", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TONOMETER = tool("tonometer", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MEDICAL_POUCH = tool("medical_pouch", () -> new MedicalContainerItem(new Item.Properties().stacksTo(1), 8));
    public static final RegistryObject<Item> FIRST_AID_KIT = tool("first_aid_kit", () -> new MedicalContainerItem(new Item.Properties().stacksTo(1), 20));
    // Второй этап
    public static final RegistryObject<Item> EMPTY_BLOOD_BAG = medical("empty_blood_bag", 16);
    public static final RegistryObject<Item> BLOOD_BAG = tool("blood_bag", () -> new faygolover.rpmedicine.item.BloodBagItem(new Item.Properties().stacksTo(1)));
    // Препараты (датапак drugs)
    public static final RegistryObject<Item> PARACETAMOL = medical("paracetamol", 16);
    public static final RegistryObject<Item> IBUPROFEN = medical("ibuprofen", 16);
    public static final RegistryObject<Item> KETOROLAC = pen("ketorolac");
    public static final RegistryObject<Item> TRAMADOL = medical("tramadol", 16);
    public static final RegistryObject<Item> NALOXONE = pen("naloxone");
    public static final RegistryObject<Item> AMOXICILLIN = medical("amoxicillin", 16);
    public static final RegistryObject<Item> CEFTRIAXONE = vial("ceftriaxone");
    public static final RegistryObject<Item> DIAZEPAM = pen("diazepam");
    public static final RegistryObject<Item> NOREPINEPHRINE = vial("norepinephrine");
    public static final RegistryObject<Item> ATROPINE = pen("atropine");
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
    /** Использованные: стерилизатор возвращает чистыми (замечание 42). */
    public static final RegistryObject<Item> DIRTY_SYRINGE = medical("dirty_syringe", 16);
    public static final RegistryObject<Item> DIRTY_TEST_TUBE = medical("dirty_test_tube", 16);
    /** Пустая шприц-ручка — на выброс. */
    public static final RegistryObject<Item> USED_PEN = medical("used_pen", 16);
    /** Шприц, набранный из флакона: препарат и мл в NBT (решения, п. 1.16). */
    public static final RegistryObject<Item> FILLED_SYRINGE = tool("filled_syringe",
            () -> new faygolover.rpmedicine.item.FilledSyringeItem(new Item.Properties().stacksTo(1)));

    /** Шприц-ручки и флаконы (решение 06.10): ручка колет сама, флакон — через многоразовый шприц. */
    public static final java.util.Set<String> PEN_IDS = java.util.Set.of("adrenaline", "morphine", "txa", "ketorolac", "naloxone", "diazepam", "atropine", "insulin");
    public static final java.util.Set<String> VIAL_IDS = java.util.Set.of("ceftriaxone", "propofol", "norepinephrine", "lidocaine", "ketamine");

    public static boolean isPen(net.minecraft.world.item.ItemStack st) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem());
        return id != null && id.getNamespace().equals(faygolover.rpmedicine.RpMedicine.MODID) && PEN_IDS.contains(id.getPath());
    }

    /** Сколько доз (= мл) осталось в ручке или флаконе. */
    public static double remainingDoses(net.minecraft.world.item.ItemStack st) {
        return st.isDamageableItem() ? (st.getMaxDamage() - st.getDamageValue()) / 10.0 : 0;
    }

    public static boolean isVial(net.minecraft.world.item.ItemStack st) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem());
        return id != null && id.getNamespace().equals(faygolover.rpmedicine.RpMedicine.MODID) && VIAL_IDS.contains(id.getPath());
    }
    public static final RegistryObject<Item> BLOOD_SAMPLE = tool("blood_sample", () -> new faygolover.rpmedicine.item.BloodSampleItem(new Item.Properties().stacksTo(1)));
    // Пули, швы (второй этап, п. 8)
    public static final RegistryObject<Item> SURGICAL_TWEEZERS = tool("surgical_tweezers", () -> new faygolover.rpmedicine.item.SurgicalInstrumentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SUTURE_KIT = tool("suture_kit", () -> new MedicalItem(new Item.Properties().durability(5)));
    public static final RegistryObject<Item> SCISSORS = tool("scissors", () -> new MedicalItem(new Item.Properties().stacksTo(1)));
    // Третий этап: анестезия (п. 3)
    public static final RegistryObject<Item> LIDOCAINE = vial("lidocaine");
    public static final RegistryObject<Item> KETAMINE = vial("ketamine");
    public static final RegistryObject<Item> PROPOFOL = vial("propofol");
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
    public static final RegistryObject<Item> INSULIN = pen("insulin");
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
                // По смыслу, в порядке оказания помощи; не попавшее в список — в конце.
                java.util.Set<String> shown = new java.util.HashSet<>();
                for (String id : ModItems.TAB_ORDER) {
                    // Органы и конечности — каждый вид отдельно (замечание 10 второй проверки).
                    if (id.equals("organ") && shown.add(id)) {
                        for (var o : faygolover.rpmedicine.core.Organ.VALUES) out.accept(unstarted(
                                faygolover.rpmedicine.item.OrganItem.create(ORGAN.get(), o, 0, null, "", 0)));
                        continue;
                    }
                    if (id.equals("severed_limb") && shown.add(id)) {
                        for (var p : new faygolover.rpmedicine.core.BodyPart[]{faygolover.rpmedicine.core.BodyPart.LEFT_ARM,
                                faygolover.rpmedicine.core.BodyPart.LEFT_LEG, faygolover.rpmedicine.core.BodyPart.LEFT_FOOT})
                            out.accept(unstarted(faygolover.rpmedicine.item.SeveredLimbItem.create(SEVERED_LIMB.get(), p, new java.util.UUID(0, 0), "", 0)));
                        continue;
                    }
                    for (RegistryObject<? extends Item> ro : ALL) {
                        if (ro.getId().getPath().equals(id) && shown.add(id)) out.accept(ro.get());
                    }
                }
                // Шприц для забора крови устарел (его заменил обычный шприц) — во вкладке не показываем.
                for (RegistryObject<? extends Item> ro : ALL)
                    if (!ro.getId().getPath().equals("blood_draw_syringe") && shown.add(ro.getId().getPath())) out.accept(ro.get());
            })
            .build());

    /** Предмет из вкладки: срок хранения пойдёт, когда возьмут в инвентарь. */
    private static net.minecraft.world.item.ItemStack unstarted(net.minecraft.world.item.ItemStack st) {
        if (st.getTag() != null) st.getTag().remove("Taken");
        return st;
    }

    /** Порядок во вкладке: наборы, раны, дыхание, таблетки, уколы, капельницы и кровь, диагностика, хирургия, протезы и органы, документы. */
    private static final String[] TAB_ORDER = {
            "first_aid_kit", "medical_pouch", "stabilization_kit", "field_surgery_kit",
            "bandage", "pressure_dressing", "hemostatic_gauze", "tourniquet", "esmarch", "occlusive_dressing", "antiseptic",
            "antibiotic_ointment", "suture_kit", "scissors", "splint",
            "decompression_needle", "airway", "ambu_bag", "laryngoscope", "endotracheal_tube", "defibrillator", "ammonia",
            "painkillers", "paracetamol", "ibuprofen", "tramadol", "amoxicillin", "cyclosporine", "glucose_tablets",
            "morphine", "adrenaline", "txa", "ketorolac", "naloxone", "ceftriaxone", "diazepam", "atropine", "lidocaine", "ketamine",
            "propofol", "insulin", "syringe", "filled_syringe", "dirty_syringe", "used_pen",
            "hospital_bed", "operating_table", "vitals_monitor", "medicine_cabinet", "medicine_crate", "sterilizer", "lab_table", "thermostat", "oxygen_tank",
            "iv_stand", "iv_catheter", "saline", "used_iv_bag", "norepinephrine", "empty_blood_bag", "blood_bag", "test_tube", "blood_sample", "dirty_test_tube", "lancet", "lab_report",
            "pulse_oximeter", "tonometer", "stethoscope", "thermometer", "glucometer", "hemoanalyzer", "portable_scanner",
            "surgical_mask", "surgical_gloves", "scalpel", "hemostat", "retractor", "surgical_tweezers", "vascular_suture",
            "surgical_drill", "osteosynthesis_kit", "chest_drain", "bone_saw",
            "prosthetic_foot", "peg_leg", "prosthetic_hook", "organ_container", "organ", "severed_limb",
            "medcard", "gm_scanner"};

    /** Шприц-ручка: 4 дозы (8 половинок), пустая становится использованной (решение 06.10). */
    private static RegistryObject<Item> pen(String name) {
        // 4 мл = 4 дозы, в десятых долях (шаг дозы 0,1).
        return tool(name, () -> new MedicalItem(new Item.Properties().durability(PEN_TENTHS)));
    }

    public static final int PEN_TENTHS = 40;
    public static final int VIAL_TENTHS = 100;

    /** Флакон препарата: 2 дозы (4 половинки), набирается многоразовым шприцем. */
    private static RegistryObject<Item> vial(String name) {
        // 10 мл = 10 доз, в десятых долях.
        return tool(name, () -> new MedicalItem(new Item.Properties().durability(VIAL_TENTHS)));
    }

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
