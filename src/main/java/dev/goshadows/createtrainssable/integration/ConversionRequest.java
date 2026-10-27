package dev.goshadows.createtrainssable.integration;

import dev.goshadows.createtrainssable.physics.MassProperties;
import dev.goshadows.createtrainssable.physics.Pose;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

/** Everything the backend needs to turn one carriage into a rigid body. */
public record ConversionRequest(ServerLevel level, UUID trainId, int carriageIndex, BlockPos anchor,
                                List<BlockPos> worldPositions, MassProperties massProperties,
                                Pose initialPose, Tuning tuning) {
}
