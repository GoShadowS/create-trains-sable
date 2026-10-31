package dev.goshadows.createtrainssable.rail;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.config.TruckSettings;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * One half of a spring coupling between two vehicles.
 *
 * <p>Two couplings facing each other within a couple of blocks hook up on their own. The link is a
 * spring and a damper along the line between the two coupling faces, and the force it makes is
 * applied to <em>both</em> bodies as one impulse and its opposite. That equality is the whole point:
 * a locomotive is held back by the wagons it pulls, which is what a coupling does and what an
 * acceleration applied to each end separately never does.</p>
 *
 * <p>Only the lower of the two block positions applies the pair, so a link is never counted twice.
 * Both ends find each other symmetrically, so either may be the one that does the work.</p>
 */
public class SpringCouplingBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    private static final int RELINK_INTERVAL = 20;

    @Nullable
    private UUID partnerSubLevel;
    @Nullable
    private BlockPos partnerPos;
    private double restLength = -1.0;
    private int relinkCountdown;
    private boolean linkedReported;

    @Nullable
    private transient ServerSubLevel ownSubLevel;
    @Nullable
    private transient RigidBodyHandle ownHandle;

    public SpringCouplingBlockEntity(BlockPos pos, BlockState state) {
        super(CTSBlockEntities.COUPLING.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Nothing to configure on the block itself.
    }

    /** A coupling only needs to look for a partner occasionally; the spring itself runs on physics. */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (relinkCountdown > 0) {
            relinkCountdown--;
            return;
        }
        relinkCountdown = RELINK_INTERVAL;
        if (partnerPos == null) {
            tryLink();
        } else {
            validatePartner();
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        ownSubLevel = subLevel;
        ownHandle = handle;
        if (level == null || level.isClientSide() || partnerPos == null || !handle.isValid()) {
            return;
        }
        // One owner per link, or the spring would be applied twice and be twice as stiff.
        if (worldPosition.compareTo(partnerPos) > 0) {
            return;
        }
        if (!(level.getBlockEntity(partnerPos) instanceof SpringCouplingBlockEntity other)) {
            unlink();
            return;
        }
        ServerSubLevel otherSubLevel = other.ownSubLevel;
        RigidBodyHandle otherHandle = other.ownHandle;
        if (otherSubLevel == null || otherHandle == null || !otherHandle.isValid()
                || otherSubLevel == subLevel) {
            return;
        }

        Vec3 here = couplingPoint(subLevel);
        Vec3 there = other.couplingPoint(otherSubLevel);
        if (here == null || there == null) {
            return;
        }
        Vec3 axis = there.subtract(here);
        double distance = axis.length();
        if (distance < 1.0e-4) {
            return;
        }
        axis = axis.scale(1.0 / distance);
        if (restLength < 0.0) {
            restLength = distance;
        }

        double extension = distance - restLength;
        double closing = velocity(handle).subtract(velocity(otherHandle)).dot(axis);
        double ownMass = Math.max(1.0e-3, subLevel.getMassTracker().getMass());
        double otherMass = Math.max(1.0e-3, otherSubLevel.getMassTracker().getMass());
        double effectiveMass = ownMass * otherMass / (ownMass + otherMass);

        TruckSettings settings = CTSConfig.trucks();
        double acceleration = extension * settings.couplingStiffness()
                + closing * settings.couplingDamping();
        double limit = settings.couplingMaxAcceleration();
        acceleration = Math.max(-limit, Math.min(limit, acceleration));

        // Equal and opposite: what the vehicle in front gains, the one behind loses.
        Vec3 impulse = axis.scale(acceleration * effectiveMass * timeStep);
        applyImpulse(subLevel, handle, here, impulse);
        applyImpulse(otherSubLevel, otherHandle, there, impulse.scale(-1.0));
    }

    /** Applies a world impulse at a world point, converting both to the plot frame Sable expects. */
    private static void applyImpulse(ServerSubLevel subLevel, RigidBodyHandle handle, Vec3 point,
                                     Vec3 impulse) {
        Vector3d localImpulse = subLevel.logicalPose()
                .transformNormalInverse(new Vector3d(impulse.x, impulse.y, impulse.z));
        Vector3d pointInPlot = subLevel.logicalPose()
                .transformPositionInverse(new Vector3d(point.x, point.y, point.z), new Vector3d());
        handle.applyImpulseAtPoint(pointInPlot, localImpulse);
    }

    private static Vec3 velocity(RigidBodyHandle handle) {
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        return new Vec3(velocity.x, velocity.y, velocity.z);
    }

    /** World position of the coupling face. */
    @Nullable
    private Vec3 couplingPoint(ServerSubLevel subLevel) {
        Direction facing = getBlockState().getValue(SpringCouplingBlock.FACING);
        Vector3d local = new Vector3d(
                worldPosition.getX() + 0.5 + facing.getStepX() * 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.5);
        Vector3d world = subLevel.logicalPose().transformPosition(local, new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    /** Finds a coupling facing back at this one within range, on another vehicle. */
    private void tryLink() {
        TruckSettings settings = CTSConfig.trucks();
        Direction facing = getBlockState().getValue(SpringCouplingBlock.FACING);
        int range = (int) Math.ceil(settings.couplingRange());
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(range, 1, range),
                worldPosition.offset(-range, -1, -range))) {
            if (pos.equals(worldPosition)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof SpringCouplingBlockEntity other)) {
                continue;
            }
            if (other.partnerPos != null || other.ownSubLevel == null || ownSubLevel == null) {
                continue;
            }
            if (other.ownSubLevel == ownSubLevel) {
                continue;
            }
            if (other.getBlockState().getValue(SpringCouplingBlock.FACING) != facing.getOpposite()) {
                continue;
            }
            Vec3 here = couplingPoint(ownSubLevel);
            Vec3 there = other.couplingPoint(other.ownSubLevel);
            if (here == null || there == null || here.distanceTo(there) > settings.couplingRange()) {
                continue;
            }
            partnerPos = pos.immutable();
            partnerSubLevel = other.ownSubLevel.getUniqueId();
            restLength = here.distanceTo(there);
            other.partnerPos = worldPosition.immutable();
            other.partnerSubLevel = ownSubLevel.getUniqueId();
            other.restLength = restLength;
            if (!linkedReported) {
                linkedReported = true;
                CreateTrainsSable.LOGGER.info("Couplings linked: {} <-> {} (rest length {})",
                        worldPosition, pos, String.format("%.2f", restLength));
            }
            return;
        }
    }

    /** Drops a link whose partner is gone or has been unloaded. */
    private void validatePartner() {
        if (partnerPos == null) {
            return;
        }
        if (level.getBlockEntity(partnerPos) instanceof SpringCouplingBlockEntity other
                && worldPosition.equals(other.partnerPos)) {
            return;
        }
        CreateTrainsSable.LOGGER.info("Coupling at {} lost its partner", worldPosition);
        unlink();
    }

    private void unlink() {
        partnerPos = null;
        partnerSubLevel = null;
        restLength = -1.0;
        linkedReported = false;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (partnerPos != null) {
            tag.putLong("PartnerPos", partnerPos.asLong());
        }
        if (partnerSubLevel != null) {
            tag.putUUID("PartnerSubLevel", partnerSubLevel);
        }
        tag.putDouble("RestLength", restLength);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        partnerPos = tag.contains("PartnerPos") ? BlockPos.of(tag.getLong("PartnerPos")) : null;
        partnerSubLevel = tag.hasUUID("PartnerSubLevel") ? tag.getUUID("PartnerSubLevel") : null;
        restLength = tag.contains("RestLength") ? tag.getDouble("RestLength") : -1.0;
    }
}
