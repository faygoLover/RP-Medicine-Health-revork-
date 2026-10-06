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

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<IvStandBlockEntity>> IV_STAND_BE = BLOCK_ENTITIES.register("iv_stand",
            () -> BlockEntityType.Builder.of(IvStandBlockEntity::new, IV_STAND.get()).build(null));

    private ModBlocks() {}
}
