package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Finds the driver of a physical vehicle, the way Create finds one inside a contraption.
 *
 * <p>Create decides whether a train may navigate by asking each carriage for
 * {@code presentConductors}; a carriage fills that in from its contraption entity, and a bridged
 * carriage has no contraption. The rule Create applies inside a contraption is however not about
 * entities: a block registered as a conductor - a blaze burner - counts when a train controls block
 * sits on one side of it and faces away from it. This class applies the same rule to the blocks of a
 * Sable body, so a bridged train can be given a driver without inventing one.</p>
 *
 * <p>The answer is a pair: whether a driver faces forward, and whether one faces backward. The
 * body's own orientation turns the world direction the carriage travels in into the local axis,
 * because a controls block stores its {@code facing} in the body's own coordinates.</p>
 */
public final class BodyConductors {

    private BodyConductors() {
    }

    /**
     * Which sides of the body have a driver, judged from the blocks in it.
     *
     * @param body    the vehicle to inspect
     * @param forward the direction the carriage travels in, in the world
     */
    public static Couple<Boolean> of(ServerSubLevel body, Vec3 forward) {
        Couple<Boolean> sides = Couple.create(false, false);
        if (body == null || forward == null || forward.lengthSqr() < 1.0e-6) {
            return sides;
        }
        Direction axis = localAxis(body, forward);
        if (axis == null) {
            return sides;
        }
        for (BlockEntitySubLevelActor actor : body.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof BlockEntity entity)) {
                continue;
            }
            Level level = entity.getLevel();
            if (level == null) {
                continue;
            }
            BlockPos pos = entity.getBlockPos();
            if (!isConductor(level.getBlockState(pos))) {
                continue;
            }
            for (Direction side : new Direction[]{axis, axis.getOpposite()}) {
                if (inControl(level, pos, side)) {
                    if (side == axis) {
                        sides.setFirst(true);
                    } else {
                        sides.setSecond(true);
                    }
                }
            }
        }
        return sides;
    }

    /** Whether a train controls block sits on {@code side} of {@code pos} and faces away from it. */
    private static boolean inControl(BlockGetter blocks, BlockPos pos, Direction side) {
        BlockState control = blocks.getBlockState(pos.relative(side));
        if (!AllBlocks.TRAIN_CONTROLS.has(control)) {
            return false;
        }
        return control.getValue(ControlsBlock.FACING) == side.getOpposite();
    }

    /** The block that can drive, by Create's own definition: it has a conductor behaviour. */
    private static boolean isConductor(BlockState state) {
        MovingInteractionBehaviour behaviour = MovingInteractionBehaviour.REGISTRY.get(state);
        return behaviour instanceof ConductorBlockInteractionBehavior conductor
                && conductor.isValidConductor(state);
    }

    /** The horizontal local axis a world direction points along, or {@code null} for a diagonal. */
    private static Direction localAxis(ServerSubLevel body, Vec3 forward) {
        Vector3d local = body.logicalPose()
                .transformNormalInverse(new Vector3d(forward.x, forward.y, forward.z));
        double ax = Math.abs(local.x);
        double az = Math.abs(local.z);
        if (Math.max(ax, az) < 1.0e-4 || Math.abs(ax - az) < 1.0e-4) {
            return null;
        }
        if (ax > az) {
            return local.x > 0 ? Direction.EAST : Direction.WEST;
        }
        return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
