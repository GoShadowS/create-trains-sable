package dev.goshadows.createtrainssable.bogey;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A bogey: the block that puts a Sable vehicle on Create's rails.
 *
 * <p>Build the vehicle you want, put bogeys under it where the wheels should be, assemble it into a
 * Sable sub-level, and it follows Create track on its own. This is deliberately the opposite
 * approach to converting Create's trains: there is no train object, no hidden contraption and
 * nothing that can be teleported, so the vehicle is exactly what was built and it derails when the
 * physics says it should.</p>
 *
 * <p>Powered bogeys take rotary power on their axle axis; the speed of that rotation is the speed
 * the bogey tries to reach. Unpowered bogeys only carry weight and roll, so a train needs at least
 * one powered bogey and can have any number of unpowered ones.</p>
 */
public class PhysicsBogeyBlock extends HorizontalKineticBlock {

    public static final Property<Direction> FACING = HorizontalKineticBlock.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    private final boolean powered;

    public PhysicsBogeyBlock(Properties properties, boolean powered) {
        super(properties);
        this.powered = powered;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean isPowered() {
        return powered;
    }

    /** The axle points sideways, so the shaft has to come in from the sides of the block. */
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhysicsBogeyBlockEntity(pos, state);
    }
}
