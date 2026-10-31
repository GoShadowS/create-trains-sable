package dev.goshadows.createtrainssable.rail;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.goshadows.createtrainssable.config.CTSConfig;
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
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * One half of a spring coupling.
 *
 * <p>A coupling finds its partner by looking for another one a couple of blocks away, facing back at
 * it and living on a different sub-level. The distance at the moment the link is made becomes the
 * rest length, so hooking up never yanks the vehicles together.</p>
 *
 * <p>The spring is expressed in acceleration rather than force so that it stays stable at the physics
 * rate Sable runs at and so that a heavy wagon is not coupled more loosely than a light one.</p>
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

    public SpringCouplingBlockEntity(BlockPos pos, BlockState state) {
        super(CTSBlockEntities.COUPLING.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

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
        validatePartner();
    }

    /** Drops a link whose partner is gone, or whose sub-levels were unloaded. */
    private void validatePartner() {
        if (partnerPos == null) {
            return;
        }
        if (level.getBlockEntity(partnerPos) instanceof SpringCouplingBlockEntity other) {
            if (other.partnerPos != null && other.partnerPos.equals(worldPosition)) {
                return;
            }
        }
        partnerPos = null;
        partnerSubLevel = null;
        restLength = -1.0;
        linkedReported = false;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide()) {
            return;
        }
        TruckSettings settings = CTSConfig.trucks();

        if (partnerPos == null) {
            tryLink(subLevel, settings);
            if (partnerPos == null) {
                return;
            }
        }

        if (!(level.getBlockEntity(partnerPos) instanceof SpringCouplingBlockEntity other)) {
            partnerPos = null;
            partnerSubLevel = null;
            restLength = -1.0;
            return;
        }
        if (!(other.level != null && other.level.getBlockEntity(other.worldPosition) == other)) {
            return;
        }

        Vec3 here = couplingPoint(subLevel);
        Vec3 there = other.couplingPoint(other.containingSubLevel());
        if (here == null || there == null) {
            return;
        }

        Vec3 delta = there.subtract(here);
        double distance = delta.length();
        if (distance < 1.0e-4) {
            return;
        }
        if (restLength < 0.0) {
            restLength = distance;
        }

        Vec3 axis = delta.scale(1.0 / distance);
        Vec3 ownVelocity = toVec(handle.getLinearVelocity(new Vector3d()));
        Vec3 otherVelocity = other.velocity();

        double extension = distance - restLength;
        double closing = otherVelocity.subtract(ownVelocity).dot(axis);
        double acceleration = extension * settings.couplingStiffness()
                + closing * settings.couplingDamping();
        double limit = settings.couplingMaxAcceleration();
        acceleration = Math.max(-limit, Math.min(limit, acceleration));

        // Only this end is pushed here: the partner runs the same calculation from its own physics
        // tick and pushes itself. Doing both sides from one end would double the spring.
        Vec3 deltaVelocity = axis.scale(acceleration * timeStep);
        handle.addLinearAndAngularVelocity(toVector(deltaVelocity), new Vector3d());
    }

    /** World position of the coupling face. */
    @Nullable
    private Vec3 couplingPoint(@Nullable ServerSubLevel subLevel) {
        if (subLevel == null) {
            return null;
        }
        Direction facing = getBlockState().getValue(SpringCouplingBlock.FACING);
        Vector3d local = new Vector3d(
                worldPosition.getX() + 0.5 + facing.getStepX() * 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.5);
        Vector3d world = subLevel.logicalPose().transformPosition(local, new Vector3d());
        return new Vec3(world.x(), world.y(), world.z());
    }

    private Vec3 velocity() {
        ServerSubLevel subLevel = containingSubLevel();
        if (subLevel == null) {
            return Vec3.ZERO;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) {
            return Vec3.ZERO;
        }
        return toVec(handle.getLinearVelocity(new Vector3d()));
    }

    @Nullable
    private ServerSubLevel containingSubLevel() {
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(this) instanceof ServerSubLevel server
                ? server : null;
    }

    /** Finds a coupling facing back at this one within range, on another vehicle. */
    private void tryLink(ServerSubLevel subLevel, TruckSettings settings) {
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
            ServerSubLevel otherSubLevel = other.containingSubLevel();
            if (otherSubLevel == null || otherSubLevel == subLevel) {
                continue;
            }
            Direction otherFacing = other.getBlockState().getValue(SpringCouplingBlock.FACING);
            // Couplings only hook up mouth to mouth.
            if (otherFacing != facing.getOpposite()) {
                continue;
            }
            Vec3 here = couplingPoint(subLevel);
            Vec3 there = other.couplingPoint(otherSubLevel);
            if (here == null || there == null || here.distanceTo(there) > settings.couplingRange()) {
                continue;
            }
            partnerPos = pos.immutable();
            partnerSubLevel = otherSubLevel.getUniqueId();
            restLength = here.distanceTo(there);
            other.partnerPos = worldPosition.immutable();
            other.partnerSubLevel = subLevel.getUniqueId();
            other.restLength = restLength;
            if (!linkedReported) {
                linkedReported = true;
                dev.goshadows.createtrainssable.CreateTrainsSable.LOGGER.info(
                        "Couplings linked: {} <-> {} (rest length {})",
                        worldPosition, partnerPos, String.format("%.2f", restLength));
            }
            return;
        }
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

    private static Vec3 toVec(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static Vector3d toVector(Vec3 vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }
}
