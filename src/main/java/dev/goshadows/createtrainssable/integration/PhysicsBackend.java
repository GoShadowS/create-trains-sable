package dev.goshadows.createtrainssable.integration;

import java.util.Optional;

/** Boundary between the mod's server logic and whichever physics engine is installed. */
public interface PhysicsBackend {

    boolean isAvailable();

    String description();

    Optional<AttachedBody> attach(ConversionRequest request);

    void shutdown();
}
