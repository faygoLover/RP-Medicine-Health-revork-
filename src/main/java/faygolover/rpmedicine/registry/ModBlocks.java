package faygolover.rpmedicine.registry;

import faygolover.rpmedicine.RpMedicine;
import faygolover.rpmedicine.hospital.IvStandBlock;
import faygolover.rpmedicine.hospital.IvStandBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Свои блоки. Мебель госпиталя мод берёт у других модов (датапак hospital_blocks); исключение — стойка
 * капельницы (замечание 35): на ней видны пакеты и от неё идёт шланг к пациенту. Модель — Industrial Hellscape.
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpMedicine.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RpMedicine.MODID);

    public static final RegistryObject<Block> IV_STAND = BLOCKS.register("iv_stand", () -> new IvStandBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    // Мебель Industrial Hellscape, забранная к себе (решения, п. 1.16): койка без коллизии подушки, стол, монитор.
    public static final RegistryObject<Block> HOSPITAL_BED = BLOCKS.register("hospital_bed", () -> new faygolover.rpmedicine.hospital.TwoPartBlock(
            furniture(), false, Block.box(0.5, 0, 0, 15.5, 10, 16), Block.box(0.5, 0, 0, 15.5, 10, 16)));
    public static final RegistryObject<Block> OPERATING_TABLE = BLOCKS.register("operating_table", () -> new faygolover.rpmedicine.hospital.TwoPartBlock(
            furniture(), false, Block.box(2, 0, 0, 14, 14, 16), Block.box(2, 0, 0, 14, 14, 16)));
    public static final RegistryObject<Block> VITALS_MONITOR = BLOCKS.register("vitals_monitor", () -> new faygolover.rpmedicine.hospital.TwoPartBlock(
            furniture(), true, Block.box(2, 0, 2, 14, 16, 14), Block.box(2, 0, 2, 14, 9.2, 14)));
    // Health & Disease (решения, п. 1.16).
    public static final RegistryObject<Block> MEDICINE_CABINET = geo(faygolover.rpmedicine.hospital.HospitalGeoBlock.Kind.CABINET);
    public static final RegistryObject<Block> MEDICINE_CRATE = geo(faygolover.rpmedicine.hospital.HospitalGeoBlock.Kind.CRATE);
    public static final RegistryObject<Block> STERILIZER = geo(faygolover.rpmedicine.hospital.HospitalGeoBlock.Kind.STERILIZER);
    public static final RegistryObject<Block> LAB_TABLE = geo(faygolover.rpmedicine.hospital.HospitalGeoBlock.Kind.LAB_TABLE);
    /** Кислородный баллон: свой, рядом с койкой — пациент дышит кислородом. */
    public static final RegistryObject<Block> OXYGEN_TANK = BLOCKS.register("oxygen_tank", () -> new faygolover.rpmedicine.hospital.SimpleFacingBlock(
            furniture(), Block.box(4.5, 0, 4.5, 11.5, 16, 11.5)));
    public static final RegistryObject<Block> THERMOSTAT = BLOCKS.register("thermostat",
            () -> new faygolover.rpmedicine.hospital.HospitalGeoBlock.Thermostat(furniture()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<faygolover.rpmedicine.hospital.HospitalStorageBlockEntity>> HOSPITAL_STORAGE_BE =
            BLOCK_ENTITIES.register("hospital_storage", () -> BlockEntityType.Builder.of(faygolover.rpmedicine.hospital.HospitalStorageBlockEntity::new,
                    MEDICINE_CABINET.get(), MEDICINE_CRATE.get(), STERILIZER.get(), LAB_TABLE.get(), THERMOSTAT.get()).build(null));

    private static BlockBehaviour.Properties furniture() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion();
    }

    private static RegistryObject<Block> geo(faygolover.rpmedicine.hospital.HospitalGeoBlock.Kind k) {
        return BLOCKS.register(k.id, () -> new faygolover.rpmedicine.hospital.HospitalGeoBlock(furniture(), k));
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<IvStandBlockEntity>> IV_STAND_BE = BLOCK_ENTITIES.register("iv_stand",
            () -> BlockEntityType.Builder.of(IvStandBlockEntity::new, IV_STAND.get()).build(null));

    private ModBlocks() {}
}
