package faygolover.rpmedicine.hospital;

import faygolover.rpmedicine.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Стойка капельницы (замечание 35): до трёх пакетов на крючках, шланг к катетеру пациента.
 * ПКМ пакетом — повесить; ПКМ пустой рукой — взять шланг (потом ПКМ по пациенту) или отсоединить;
 * Shift+ПКМ пустой рукой — снять последний пакет.
 */
public class IvStandBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Верхняя половина: невидимая (модель рисует нижняя), но со своей коллизией и кликом (замечание 06.10). */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty UPPER =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("upper");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(6.5, 0, 6.5, 9.5, 16, 9.5));
    private static final VoxelShape COLLISION = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(7, 0, 7, 9, 16, 9));
    private static final VoxelShape TOP_SHAPE = Shapes.or(Block.box(6.5, 0, 6.5, 9.5, 12, 9.5), Block.box(2, 10, 2, 14, 16, 14));
    private static final VoxelShape TOP_COLLISION = Block.box(7, 0, 7, 9, 15, 9);

    public IvStandBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UPPER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, UPPER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos above = ctx.getClickedPos().above();
        if (above.getY() >= ctx.getLevel().getMaxBuildHeight() || !ctx.getLevel().getBlockState(above).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState st, @Nullable net.minecraft.world.entity.LivingEntity by,
                            net.minecraft.world.item.ItemStack stack) {
        level.setBlock(pos.above(), st.setValue(UPPER, true), 3);
    }

    /** Половины держат друг друга: нет пары — блок исчезает (как дверь). */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState st, Direction dir, BlockState other, net.minecraft.world.level.LevelAccessor level,
                                  BlockPos pos, BlockPos otherPos) {
        boolean upper = st.getValue(UPPER);
        if (dir == (upper ? Direction.DOWN : Direction.UP) && !(other.is(this) && other.getValue(UPPER) != upper))
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(st, dir, other, level, pos, otherPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return st.getValue(UPPER) ? TOP_SHAPE : SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return st.getValue(UPPER) ? TOP_COLLISION : COLLISION;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState st) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.CONSUME;
        BlockPos base = st.getValue(UPPER) ? pos.below() : pos;
        return IvStandService.use((ServerPlayer) player, base) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState st, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!st.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof IvStandBlockEntity be) be.dropAll();
        super.onRemove(st, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return st.getValue(UPPER) ? null : new IvStandBlockEntity(pos, st);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState st, BlockEntityType<T> type) {
        if (level.isClientSide || st.getValue(UPPER) || type != ModBlocks.IV_STAND_BE.get()) return null;
        return (l, p, s, be) -> ((IvStandBlockEntity) be).serverTick();
    }
}
