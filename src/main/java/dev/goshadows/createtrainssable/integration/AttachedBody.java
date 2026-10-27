package dev.goshadows.createtrainssable.integration;

import dev.goshadows.createtrainssable.physics.MassProperties;
import dev.goshadows.createtrainssable.physics.Pose;

/** Opaque handle to one simulated carriage. The core never sees Sable classes. */
public interface AttachedBody {

    String id();

    boolean isValid();

    Pose samplePose();

    /**
     * Supplies the carriage's estimated mass profile. Sable's datapack-driven MassTracker
     * stays authoritative for the body itself; the profile scales the wheel forces this mod adds.
     */
    void applyMassProfile(MassProperties properties);

    void release(ReleaseReason reason);
}
