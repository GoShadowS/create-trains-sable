package dev.goshadows.createtrainssable.physics;

/** One voxel of a carriage, with an estimated mass in Sable's kpg unit. */
public record WeightedBlock(Point3 position, double mass) {
}
