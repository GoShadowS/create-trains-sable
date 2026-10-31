package dev.goshadows.createtrainssable.rail;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A truck: the block that puts a Sable vehicle on Create's rails.
 *
 * <p>Build the vehicle, put trucks where the axles belong, give the driven ones rotation and assemble
 * the whole thing into a sub-level. There is no train object, no hidden contraption and nothing that
 * can be teleported, so the vehicle is exactly what was built and it leaves the rails when the
 * physics says it should.</p>
 *
 * <p>A driven truck takes rotary power on its axle axis; the speed of that rotation is the speed the
 * vehicle tries to reach. A rolling truck carries weight, coasts and brakes. A vehicle needs at least
 * one driven truck.</p>
 */
public class TruckBlock extends HorizontalKineticBlock implements IBE<TruckBlockEntity> {

    public static final Property<Direction> FACING = HorizontalKineticBlock.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0);

    private final boolean driven;

    public TruckBlock(Properties properties, boolean driven) {
        super(properties);
        this.driven = driven;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** True for a truck that converts rotation into motion. */
    public boolean isDriven() {
        return driven;
    }

    /** The axle runs across the vehicle, so the shaft has to come in from the sides. */
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getClockWise().getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<TruckBlockEntity> getBlockEntityClass() {
        return TruckBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TruckBlockEntity> getBlockEntityType() {
        return CTSBlockEntities.TRUCK.get();
    }
}
