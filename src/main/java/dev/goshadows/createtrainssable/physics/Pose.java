package dev.goshadows.createtrainssable.physics;

/** A rigid body pose sampled from the physics world. Velocities are in m/s and rad/s. */
public record Pose(Point3 position, Quat orientation, Point3 linearVelocity, Point3 angularVelocity) {

    public static final Pose IDENTITY = new Pose(Point3.ZERO, Quat.IDENTITY, Point3.ZERO, Point3.ZERO);

    public double speed() {
        return linearVelocity.length();
    }

    public double angularSpeed() {
        return angularVelocity.length();
    }

    public double tiltDegrees() {
        return orientation.tiltDegrees();
    }
}
