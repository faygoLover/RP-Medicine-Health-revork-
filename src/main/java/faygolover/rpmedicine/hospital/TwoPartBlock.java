package faygolover.rpmedicine.hospital;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Двухблочная мебель госпиталя (модели Industrial Hellscape, YellowUboat, MIT): койка и операционный стол
 * лежат вдоль (FACING — куда изголовье; HEAD — изголовье), монитор стоит в высоту (HEAD — верх).
 * Половины держат друг друга, как дверь.
 */
public class TwoPartBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty HEAD = BooleanProperty.create("head");

    private final boolean vertical;
    private final VoxelShape headShape, footShape;

    public TwoPartBlock(Properties props, boolean vertical, VoxelShape footShape, VoxelShape headShape) {
        super(props);
        this.vertical = vertical;
        this.footShape = footShape;
        this.headShape = headShape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEAD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HEAD);
    }

    /** Где вторая половина от этой. */
    public Direction toOther(BlockState st) {
        if (vertical) return st.getValue(HEAD) ? Direction.DOWN : Direction.UP;
        Direction f = st.getValue(FACING);
        return st.getValue(HEAD) ? f.getOpposite() : f;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Койка: ставишь ноги, изголовье — дальше по взгляду. Монитор: экраном к игроку.
        Direction look = ctx.getHorizontalDirection();
        BlockState st = defaultBlockState().setValue(FACING, vertical ? look.getOpposite() : look);
        BlockPos other = ctx.getClickedPos().relative(toOther(st));
        if (!ctx.getLevel().getWorldBorder().isWithinBounds(other) || !ctx.getLevel().getBlockState(other).canBeReplaced(ctx)) return null;
        return st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState st, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.relative(toOther(st)), st.setValue(HEAD, true), 3);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState st, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == toOther(st) && !(other.is(this) && other.getValue(HEAD) != st.getValue(HEAD) && other.getValue(FACING) == st.getValue(FACING)))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(st, dir, other, level, pos, otherPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return rotate(st.getValue(HEAD) ? headShape : footShape, st.getValue(FACING));
    }

    /** Форма задана для FACING=north — повернуть. */
    public static VoxelShape rotate(VoxelShape s, Direction f) {
        if (f == Direction.NORTH) return s;
        VoxelShape[] out = {net.minecraft.world.phys.shapes.Shapes.empty()};
        int turns = switch (f) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        s.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double ax1 = x1, az1 = z1, ax2 = x2, az2 = z2;
            for (int i = 0; i < turns; i++) {
                double nx1 = 1 - az2, nz1 = ax1, nx2 = 1 - az1, nz2 = ax2;
                ax1 = nx1;
                az1 = nz1;
                ax2 = nx2;
                az2 = nz2;
            }
            out[0] = net.minecraft.world.phys.shapes.Shapes.or(out[0], net.minecraft.world.phys.shapes.Shapes.box(ax1, y1, az1, ax2, y2, az2));
        });
        return out[0];
    }

    public boolean vertical() {
        return vertical;
    }
}
