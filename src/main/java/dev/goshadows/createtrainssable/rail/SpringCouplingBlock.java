package dev.goshadows.createtrainssable.rail;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * A spring coupling between two Sable vehicles.
 *
 * <p>Two couplings facing each other within a couple of blocks hook up on their own: the vehicles are
 * then pulled and pushed by a spring and a damper, so a locomotive actually drags its wagons and a
 * shunt at the back of the train is felt at the front. Unlike a rigid joint this is forgiving - it
 * stretches under load and settles, which is what a real screw coupling does.</p>
 */
public class SpringCouplingBlock extends HorizontalDirectionalBlock
        implements IBE<SpringCouplingBlockEntity> {

    public static final MapCodec<SpringCouplingBlock> CODEC =
            simpleCodec(SpringCouplingBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Block.box(2.0, 1.0, 1.0, 14.0, 10.0, 13.0);

    public SpringCouplingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<SpringCouplingBlockEntity> getBlockEntityClass() {
        return SpringCouplingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpringCouplingBlockEntity> getBlockEntityType() {
        return CTSBlockEntities.COUPLING.get();
    }
}
