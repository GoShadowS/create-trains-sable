package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.rail.Vec;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The player's way to bridge a train to the bodies it should ride.
 *
 * <p>Sneak-right-click any block of an assembled vehicle with the assembly tool. If that vehicle is
 * already part of a bridged train, the train is unbridged; otherwise the nearest train whose route
 * touches the vehicle is bridged to it and to the other bodies of the same train, taken in order
 * along the direction the train faces.</p>
 *
 * <p>This is deliberately a debug action on a debug tool, and the rules are stated rather than
 * guessed at:</p>
 *
 * <ul>
 *   <li>the clicked block must belong to a Sable body, so the tool knows which vehicle is meant;</li>
 *   <li>the train is the nearest one in Create's railway manager whose leading carriage is within
 *   {@link #SEARCH_RADIUS} blocks of that vehicle, on the same dimension, and is not already
 *   bridged;</li>
 *   <li>one body per carriage, and there must be exactly as many free bodies in the same radius as
 *   the train has carriages - otherwise nothing happens and the player is told the counts.</li>
 * </ul>
 *
 * <p>The physics never learns anything from this class except which body belongs to which carriage;
 * everything else the bridge does on its own.</p>
 */
public final class BridgeTool {

    /** How far from the clicked vehicle a train or another body may be and still be found. */
    private static final double SEARCH_RADIUS = 64.0;

    /** What a click did, so the caller can colour a message and the harness can assert on it. */
    public enum Status {
        BRIDGED,
        UNBRIDGED,
        NO_VEHICLE,
        NO_TRAIN,
        COUNT_MISMATCH,
        FAILED
    }

    /** The result of one click. */
    public record Outcome(Status status, String detail, @Nullable Train train) {

        public boolean ok() {
            return status == Status.BRIDGED || status == Status.UNBRIDGED;
        }
    }

    private BridgeTool() {
    }

    /**
     * Bridges or unbinds, depending on what the clicked vehicle is already doing.
     *
     * @param level  the server level the click happened in
     * @param pos    the block that was clicked
     * @param player the player to report to, or {@code null} when a script is the actor
     */
    public static Outcome toggle(ServerLevel level, BlockPos pos, @Nullable Player player) {
        ServerSubLevel body = findBody(level, pos);
        if (body == null) {
            return report(player, Status.NO_VEHICLE, null,
                    "That block is not part of an assembled vehicle.");
        }

        Train carried = TrainBridge.trainOf(body);
        if (carried != null) {
            TrainBridge.detach(carried);
            return report(player, Status.UNBRIDGED, carried, "Train " + shortId(carried)
                    + " no longer rides these bodies; its carriages moved back to Create.");
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return report(player, Status.FAILED, null, "This dimension has no sub-levels.");
        }
        Train train = nearestTrain(level, body);
        if (train == null) {
            return report(player, Status.NO_TRAIN, null, "No unbridged train within "
                    + (int) SEARCH_RADIUS + " blocks of this vehicle.");
        }

        int wanted = train.carriages.size();
        List<ServerSubLevel> bodies = bodiesAlong(container, level, body, forward(train), wanted);
        if (bodies.size() < wanted) {
            return report(player, Status.COUNT_MISMATCH, train, "Train " + shortId(train) + " has " + wanted
                    + " carriage(s), but only " + bodies.size() + " free vehicle(s) are within "
                    + (int) SEARCH_RADIUS + " blocks.");
        }

        try {
            TrainBridge.bridge(train, bodies.subList(0, wanted));
        } catch (IllegalArgumentException e) {
            CreateTrainsSable.LOGGER.warn("Could not bridge train {}: {}", train.id, e.getMessage());
            return report(player, Status.FAILED, train, "Could not bridge: " + e.getMessage());
        }
        return report(player, Status.BRIDGED, train, "Train " + shortId(train) + " now rides " + wanted
                + " physical vehicle(s). Its wheels decide how it moves.");
    }

    /**
     * The sub-level the clicked block belongs to, or {@code null} when it belongs to none.
     *
     * <p>An assembled vehicle has no blocks in the world: they live in the plot, at plot
     * coordinates, so the question "which body is this position in" is answered by asking every
     * body whether it has an actor there.</p>
     */
    @Nullable
    public static ServerSubLevel findBody(ServerLevel level, BlockPos pos) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }
            for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
                if (actor instanceof BlockEntity blockEntity && blockEntity.getBlockPos().equals(pos)) {
                    return subLevel;
                }
            }
        }
        return null;
    }

    /**
     * The nearest train that is not bridged and whose leading carriage is near the vehicle.
     *
     * <p>Distance is measured to the leading carriage's anchor, which is where the train physically
     * is; comparing graphs would need the vehicle to be on the rail already, which it is not
     * necessarily at the moment the player asks.</p>
     */
    @Nullable
    public static Train nearestTrain(ServerLevel level, ServerSubLevel body) {
        Vec centre = TrainBridge.centre(body);
        Train best = null;
        double bestDistance = SEARCH_RADIUS;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.invalid || train.graph == null || TrainBridge.isBridged(train)) {
                continue;
            }
            Vec3 anchor = leadingAnchor(train);
            if (anchor == null) {
                continue;
            }
            if (train.carriages.isEmpty()) {
                continue;
            }
            Carriage first = train.carriages.getFirst();
            if (first.getLeadingPoint().node1 == null
                    || !first.getLeadingPoint().node1.getLocation().dimension
                    .equals(level.dimension())) {
                continue;
            }
            double distance = Math.sqrt(Math.pow(anchor.x - centre.x(), 2)
                    + Math.pow(anchor.y - centre.y(), 2) + Math.pow(anchor.z - centre.z(), 2));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = train;
            }
        }
        return best;
    }

    /**
     * Free bodies near the given one, leading first.
     *
     * <p>"Leading" means furthest along the direction the train faces, so the first carriage of the
     * train is paired with the vehicle at the front of the consist. Bodies already carrying a train
     * and bodies without a single truck are skipped: a body with no axle could not hold itself on
     * the rail and would fall over on the first tick.</p>
     */
    private static List<ServerSubLevel> bodiesAlong(ServerSubLevelContainer container, ServerLevel level,
                                                    ServerSubLevel seed, Vec forward, int wanted) {
        List<ServerSubLevel> candidates = new ArrayList<>();
        Vec seedCentre = TrainBridge.centre(seed);
        for (ServerSubLevel candidate : container.getAllSubLevels()) {
            if (candidate.isRemoved() || candidate == seed) {
                continue;
            }
            if (TrainBridge.trainOf(candidate) != null) {
                continue;
            }
            if (countTrucks(candidate) == 0) {
                continue;
            }
            if (TrainBridge.centre(candidate).sub(seedCentre).length() > SEARCH_RADIUS) {
                continue;
            }
            candidates.add(candidate);
        }
        candidates.add(seed);
        candidates.sort(Comparator.comparingDouble((ServerSubLevel body) ->
                TrainBridge.centre(body).sub(seedCentre).dot(forward)).reversed());
        return candidates;
    }

    /** Direction the train faces, from its trailing point to its leading point. */
    public static Vec forward(Train train) {
        if (train.graph == null || train.carriages.isEmpty()) {
            return Vec.ZERO;
        }
        CarriageBogey leading = train.carriages.getFirst().leadingBogey();
        if (leading.leading().node1 == null || leading.trailing().node1 == null) {
            return Vec.ZERO;
        }
        Vec3 front = leading.leading().getPosition(train.graph);
        Vec3 back = leading.trailing().getPosition(train.graph);
        Vec direction = Vec.of(front.subtract(back));
        return direction.lengthSqr() < 1.0e-6 ? Vec.ZERO : direction.normalized();
    }

    @Nullable
    private static Vec3 leadingAnchor(Train train) {
        if (train.carriages.isEmpty()) {
            return null;
        }
        return train.carriages.getFirst().leadingBogey().getAnchorPosition();
    }

    private static int countTrucks(ServerSubLevel body) {
        int count = 0;
        for (BlockEntitySubLevelActor actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof dev.goshadows.createtrainssable.rail.TruckBlockEntity) {
                count++;
            }
        }
        return count;
    }

    private static Outcome report(@Nullable Player player, Status status, @Nullable Train train,
                                  String message) {
        if (player != null) {
            ChatFormatting colour = status == Status.BRIDGED || status == Status.UNBRIDGED
                    ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.displayClientMessage(Component.literal(message).withStyle(colour), true);
        }
        CreateTrainsSable.LOGGER.info("Bridge tool: {} ({})", status, message);
        return new Outcome(status, message, train);
    }

    private static String shortId(Train train) {
        return train.id.toString().substring(0, 8);
    }
}
