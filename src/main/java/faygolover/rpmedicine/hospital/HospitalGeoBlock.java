package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.menu.MedicalStorageMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Блоки госпиталя из Health & Disease (JEDIGD, MIT), забранные к себе (решения, п. 1.16): аптечный шкаф и
 * ящик (хранилище только для медицины), стерилизатор, стол-лаборатория, термостат для пробирок.
 * 3D-модель рисует {@code HospitalGeoRenderer}; у термостата — обычная модель с пробирками.
 */
public class HospitalGeoBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Kind {
        CABINET("medicine_cabinet", 27, MedicalStorageMenu.MEDICAL, Block.box(3, 1.5, 9.5, 13, 15.5, 16)),
        CRATE("medicine_crate", 27, MedicalStorageMenu.MEDICAL, Block.box(0, 0, 0.5, 16, 16, 16)),
        STERILIZER("sterilizer", 0, MedicalStorageMenu.MEDICAL, Block.box(0, 0, 0, 16, 16, 16)),
        LAB_TABLE("lab_table", 0, MedicalStorageMenu.MEDICAL, Block.box(0, 0, 0, 16, 16, 16)),
        THERMOSTAT("thermostat", 9, MedicalStorageMenu.BLOOD_SAMPLES, Block.box(2, 0, 4, 14, 8, 12));

        public final String id;
        public final int slots;
        public final byte filter;
        final VoxelShape shape;

        Kind(String id, int slots, byte filter, VoxelShape shape) {
            this.id = id;
            this.slots = slots;
            this.filter = filter;
            this.shape = shape;
        }
    }

    public final Kind kind;

    public HospitalGeoBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return TwoPartBlock.rotate(kind.shape, st.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState st) {
        return kind == Kind.THERMOSTAT ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof HospitalStorageBlockEntity be)) return InteractionResult.PASS;
        if (kind.slots <= 0) {
            // Стерилизатор и стол: крышка открывается и закрывается (сама работа — ПКМ предметом, InteractionHandler).
            if (!level.isClientSide) {
                level.blockEvent(pos, this, 1, 1);
                level.scheduleTick(pos, this, 30);
            }
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) net.minecraftforge.network.NetworkHooks.openScreen((ServerPlayer) player, be, buf -> {
            buf.writeVarInt(kind.slots / 9);
            buf.writeByte(kind.filter);
        });
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState st, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource rnd) {
        level.blockEvent(pos, this, 1, 0);
    }

    /** Открыть или закрыть крышку для всех (стерилизатор после работы). */
    public static void animate(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof HospitalGeoBlock b && !level.isClientSide) {
            level.blockEvent(pos, b, 1, 1);
            level.scheduleTick(pos, b, 30);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean triggerEvent(BlockState st, Level level, BlockPos pos, int id, int data) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, data);
    }

    /** Содержимое изменилось (термостат показывает пробирки). */
    public void contentsChanged(Level level, BlockPos pos, HospitalStorageBlockEntity be) {}

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState st, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!st.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HospitalStorageBlockEntity be)
            Containers.dropContents(level, pos, be);
        super.onRemove(st, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new HospitalStorageBlockEntity(pos, st);
    }

    /** Термостат: видно, сколько пробирок внутри (модели H&D tube1–tube8). */
    public static class Thermostat extends HospitalGeoBlock {
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty TUBES =
                net.minecraft.world.level.block.state.properties.IntegerProperty.create("tubes", 0, 8);

        public Thermostat(Properties props) {
            super(props, Kind.THERMOSTAT);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TUBES, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, TUBES);
        }

        @Override
        public void contentsChanged(Level level, BlockPos pos, HospitalStorageBlockEntity be) {
            int n = 0;
            for (var st : be.items()) if (!st.isEmpty()) n++;
            BlockState cur = level.getBlockState(pos);
            if (cur.is(this) && cur.getValue(TUBES) != Math.min(8, n)) level.setBlock(pos, cur.setValue(TUBES, Math.min(8, n)), 3);
        }
    }
}
