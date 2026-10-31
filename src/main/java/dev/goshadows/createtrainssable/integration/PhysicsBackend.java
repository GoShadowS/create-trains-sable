package dev.goshadows.createtrainssable.integration;

import java.util.Optional;

/** Boundary between the mod's server logic and whichever physics engine is installed. */
public interface PhysicsBackend {

    boolean isAvailable();

    String description();

    /**
     * True when {@link AttachedBody#drive(dev.goshadows.createtrainssable.physics.Point3,
     * dev.goshadows.createtrainssable.physics.Point3)} actually moves the body. Backends without
     * force support leave the caller on the kinematic path.
     */
    default boolean supportsForces() {
        return false;
    }

    Optional<AttachedBody> attach(ConversionRequest request);

    void shutdown();
}
