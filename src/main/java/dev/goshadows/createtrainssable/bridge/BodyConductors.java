package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

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
        if (body == null || forward == null || forward.lengthSqr() < 1.0e-6) {
            return sides;
        }
        Direction axis = localAxis(body, forward);
        BoundingBox3ic bounds = body.getPlot().getBoundingBox();
        if (axis == null || bounds.volume() <= 0 || bounds.volume() > MAX_BLOCKS) {
            return sides;
        }
        Blocks blocks = new Blocks(body);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isConductor(blocks, pos)) {
                        continue;
                    }
                    for (Direction side : new Direction[]{axis, axis.getOpposite()}) {
                        if (inControl(blocks, pos, side)) {
                            sides.set(side == axis, true);
                        }
                    }
                }
            }
        }
        return sides;
    }

    /**
     * A one-line description of what the scan saw, for the harness.
     *
     * <p>It exists because "no driver found" has several very different causes - an empty bounding
     * box, a look at the wrong coordinates, a burner that is not lit, a controls block facing the
     * wrong way - and a failed assertion alone does not say which.</p>
     */
    public static String probe(ServerSubLevel body, Vec3 forward) {
        if (body == null) {
            return "no body";
        }
        BoundingBox3ic bounds = body.getPlot().getBoundingBox();
        Blocks blocks = new Blocks(body);
        int nonAir = 0;
        int burners = 0;
        int lit = 0;
        int controls = 0;
        if (bounds.volume() > 0 && bounds.volume() <= MAX_BLOCKS) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = blocks.at(pos);
                        if (state == null || state.isAir()) {
                            continue;
                        }
                        nonAir++;
                        if (AllBlocks.TRAIN_CONTROLS.has(state)) {
                            controls++;
                        }
                        if (AllBlocks.BLAZE_BURNER.has(state)) {
                            burners++;
                            if (isConductor(blocks, pos)) {
                                lit++;
                            }
                        }
                    }
                }
            }
        }
        return "bounds=" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + ".."
                + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ()
                + " volume=" + bounds.volume()
                + " chunks=" + blocks.size()
                + " non_air=" + nonAir + " burners=" + burners + " lit=" + lit
                + " controls=" + controls
                + " axis=" + (forward == null ? null : localAxis(body, forward))
                + " sides=" + of(body, forward);
    }

    /**
     * The chunks of a body, by their own position.
     *
     * <p>A body's blocks are not in any level a mod can query through {@code World}: they sit in the
     * chunks of the plot, and those chunks answer for the coordinates their own blocks carry.
     * {@code EmbeddedPlotLevelAccessor} looks like the tool for this and is not - its coordinates are
     * offset by the plot centre, so it reads the world around the vehicle instead of the vehicle.</p>
     */
    private static final class Blocks {

        private final Map<Long, LevelChunk> chunks = new HashMap<>();

        private Blocks(ServerSubLevel body) {
            for (PlotChunkHolder holder : body.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk != null) {
                    this.chunks.put(chunk.getPos().toLong(), chunk);
                }
            }
        }

        private BlockState at(BlockPos pos) {
            LevelChunk chunk = this.chunks.get(
                    ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            return chunk == null ? null : chunk.getBlockState(pos);
        }

        private int size() {
            return this.chunks.size();
        }
    }

    /** Whether a train controls block sits on {@code side} of {@code pos} and faces away from it. */
    private static boolean inControl(Blocks blocks, BlockPos pos, Direction side) {
        BlockState control = blocks.at(pos.relative(side));
        if (control == null || !AllBlocks.TRAIN_CONTROLS.has(control)) {
            return false;
        }
        return control.getValue(ControlsBlock.FACING) == side.getOpposite();
    }

    /**
     * The block that can drive, by Create's own definition.
     *
     * <p>Create's registry is keyed by block and a burner only counts while it is lit, so the
     * behaviour is asked whether this particular state is a conductor.</p>
     */
    private static boolean isConductor(Blocks blocks, BlockPos pos) {
        BlockState state = blocks.at(pos);
        if (state == null || state.isAir()) {
            return false;
        }
        MovingInteractionBehaviour behaviour =
                MovingInteractionBehaviour.REGISTRY.get(state.getBlock());
        return behaviour instanceof ConductorBlockInteractionBehavior conductor
                && conductor.isValidConductor(state);
    }

    /** The horizontal local axis a world direction points along, or {@code null} for a diagonal. */
    private static Direction localAxis(ServerSubLevel body, Vec3 forward) {
        org.joml.Vector3d local = body.logicalPose()
                .transformNormalInverse(new org.joml.Vector3d(forward.x, forward.y, forward.z));
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
