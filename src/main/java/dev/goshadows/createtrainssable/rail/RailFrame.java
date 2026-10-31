package dev.goshadows.createtrainssable.rail;

import net.minecraft.world.phys.Vec3;

/**
 * The frame of a rail at one point: where the rail head is and which way it goes there.
 *
 * <p>{@link RailTrack} is the implementation backed by Create's track graph; the wheel/rail contact
 * only needs this much, which keeps the contact arithmetic free of the game and testable on its
 * own.</p>
 */
public interface RailFrame {

    /** World position of the rail head. */
    Vec3 point();

    /** Unit tangent along the rail. */
    Vec3 tangent();

    /** Unit up direction of the rail. */
    Vec3 up();

    /** Unit sideways direction, {@code tangent x up}. */
    Vec3 lateral();

    /** Curvature in 1/blocks; zero on straight track. */
    double curvature();

    /** True when the curve cannot hold a vehicle at this speed, in m/s. */
    default boolean isOverspeed(double speed, double lateralLimit) {
        return curvature() * speed * speed > lateralLimit;
    }
}
