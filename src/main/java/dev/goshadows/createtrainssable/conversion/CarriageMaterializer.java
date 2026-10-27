package dev.goshadows.createtrainssable.conversion;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.goshadows.createtrainssable.physics.MassProperties;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Pose;
import dev.goshadows.createtrainssable.physics.Quat;
import dev.goshadows.createtrainssable.physics.Tuning;
import dev.goshadows.createtrainssable.physics.WeightedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes a disposable copy of a carriage's virtual blocks into the world so Sable's
 * assembleBlocks() has real blocks to move into a plot.
 *
 * <p>The CarriageContraptionEntity is left untouched and hidden: it stays the owner of the
 * schedule, seats and contraption inventories, which makes a later resync possible. Copies of
 * block-entity NBT are restored into the plot so chests and tanks are not empty wrecks.</p>
 */
public final class CarriageMaterializer {

    public record Result(BlockPos anchor, List<BlockPos> positions, MassProperties massProperties) {
    }

    private CarriageMaterializer() {
    }

    public static Optional<Result> materialize(ServerLevel level, Carriage carriage, Tuning tuning) {
        CarriageContraptionEntity entity = carriage.anyAvailableEntity();
        if (entity == null || !(entity.getContraption() instanceof CarriageContraption contraption)) {
            return Optional.empty();
        }

        Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks = contraption.getBlocks();
        List<BlockPos> positions = new ArrayList<>();
        List<WeightedBlock> weights = new ArrayList<>();
        Set<BlockPos> occupied = new HashSet<>();

        for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : blocks.entrySet()) {
            StructureTemplate.StructureBlockInfo info = entry.getValue();
            if (info.state().isAir()) {
                continue;
            }
            Vec3 world = entity.toGlobalVector(Vec3.atLowerCornerOf(entry.getKey()), 1.0F);
            BlockPos target = BlockPos.containing(world);
            // Never overwrite terrain or rails: a partial wreck is recoverable, a hole is not.
            if (!occupied.add(target) || !level.getBlockState(target).isAir()) {
                continue;
            }

            BlockState state = info.state();
            level.setBlock(target, state, Block.UPDATE_ALL);
            CompoundTag tag = info.nbt();
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (tag != null && blockEntity != null) {
                CompoundTag placed = tag.copy();
                placed.putInt("x", target.getX());
                placed.putInt("y", target.getY());
                placed.putInt("z", target.getZ());
                blockEntity.loadWithComponents(placed, level.registryAccess());
            }

            positions.add(target);
            double mass = Math.max(tuning.traction().minimumBlockMass(),
                    state.getDestroySpeed(level, target) * tuning.traction().blockMassScale());
            weights.add(new WeightedBlock(new Point3(target.getX() + 0.5, target.getY() + 0.5,
                    target.getZ() + 0.5), mass));
        }

        if (positions.isEmpty()) {
            return Optional.empty();
        }
        CreateTrainsSable.LOGGER.debug("Materialised {} blocks for carriage {}", positions.size(), carriage.id);
        return Optional.of(new Result(BlockPos.containing(entity.getAnchorVec()), positions,
                MassProperties.fromBlocks(weights, MassProperties.CUBE_INERTIA_FACTOR)));
    }

    /** Undo a materialisation when the backend refuses the conversion. */
    public static void rollback(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos position : positions) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Initial rigid-body velocity: Create's signed speed along the current track direction. */
    public static Pose initialPose(Carriage carriage, Train train, double speedMetresPerSecond) {
        Vec3 anchor = carriage.anyAvailableEntity() != null
                ? carriage.anyAvailableEntity().position() : Vec3.ZERO;
        Vec3 direction = carriage.getLeadingPoint().edge != null
                ? carriage.getLeadingPoint().edge.getDirection(true) : new Vec3(1.0, 0.0, 0.0);
        if (train.speed < 0.0) {
            direction = direction.scale(-1.0);
        }
        direction = direction.normalize().scale(speedMetresPerSecond);
        return new Pose(new Point3(anchor.x, anchor.y, anchor.z), Quat.IDENTITY,
                new Point3(direction.x, direction.y, direction.z), Point3.ZERO);
    }
}
