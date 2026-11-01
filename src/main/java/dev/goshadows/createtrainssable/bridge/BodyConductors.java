package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Finds the driver of a physical vehicle, the way Create finds one inside a contraption.
 *
 * <p>Create decides whether a train may navigate by asking each carriage for
 * {@code presentConductors}; a carriage fills that in from its contraption entity, and a bridged
 * carriage has no contraption. The rule Create applies inside a contraption is however not about
 * entities: a block registered as a conductor - a lit blaze burner - counts when a train controls
 * block sits on one side of it and faces away from it. This class applies the same rule to the blocks
 * of a Sable body, so a bridged train can be given a driver without inventing one.</p>
 *
 * <p>The answer is a pair: whether a driver faces forward, and whether one faces backward. The body's
 * own orientation turns the world direction the carriage travels in into the local axis, because a
 * controls block stores its {@code facing} in the body's own coordinates.</p>
 */
public final class BodyConductors {

    /** No real vehicle is anywhere near this; a larger box means the bounds are wrong. */
    private static final int MAX_BLOCKS = 1 << 16;

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
        if (!(body instanceof KinematicContraption contraption) || forward == null
                || forward.lengthSqr() < 1.0e-6) {
            return sides;
        }
        BlockGetter blocks = contraption.sable$blockGetter();
        Direction axis = localAxis(body, forward);
        if (blocks == null || axis == null) {
            return sides;
        }
        BoundingBox3ic bounds = body.getPlot().getBoundingBox();
        if (bounds.volume() <= 0 || bounds.volume() > MAX_BLOCKS) {
            return sides;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isConductor(blocks.getBlockState(pos))) {
                        continue;
                    }
                    for (Direction side : new Direction[]{axis, axis.getOpposite()}) {
                        if (inControl(blocks, pos, side)) {
                            if (side == axis) {
                                sides.setFirst(true);
                            } else {
                                sides.setSecond(true);
                            }
                        }
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

    /**
     * The block that can drive, by Create's own definition.
     *
     * <p>Create's registry is keyed by block and the burner only counts while it is lit, so the
     * behaviour is asked whether this particular state is a conductor.</p>
     */
    private static boolean isConductor(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        MovingInteractionBehaviour behaviour =
                MovingInteractionBehaviour.REGISTRY.get(state.getBlock());
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
