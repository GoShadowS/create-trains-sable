package dev.goshadows.createtrainssable.integration;

import dev.goshadows.createtrainssable.physics.MassProperties;
import dev.goshadows.createtrainssable.physics.Point3;
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

    /**
     * Moves the body to a pose and clears its momentum. Used while Create still drives the train:
     * the sub-level is the visible, colliding structure, but the kinematic train owns the path.
     */
    void follow(Pose pose);

    /**
     * Applies the forces that hold a running train on its rails.
     *
     * <p>Unlike {@link #follow(Pose)} this never moves the body: it only pushes and turns it, so a
     * collision or an overloaded curve can win against the rails. The backend spreads the force
     * over its substeps; callers supply new values once per game tick.</p>
     *
     * @param force  force at the centre of mass, world space
     * @param torque torque about the centre of mass, world space
     */
    void drive(Point3 force, Point3 torque);

    /** Hands the body to the physics engine with the train's momentum. */
    void launch(Point3 linearVelocity);

    void release(ReleaseReason reason);
}
