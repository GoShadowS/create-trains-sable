package dev.goshadows.createtrainssable.physics;

/** Unit quaternion in (x, y, z, w) order, matching JOML and Sable's Pose3d. */
public record Quat(double x, double y, double z, double w) {

    public static final Quat IDENTITY = new Quat(0.0, 0.0, 0.0, 1.0);

    public Point3 rotate(Point3 vector) {
        double vx = vector.x();
        double vy = vector.y();
        double vz = vector.z();
        double tx = 2.0 * (y * vz - z * vy);
        double ty = 2.0 * (z * vx - x * vz);
        double tz = 2.0 * (x * vy - y * vx);
        return new Point3(
                vx + w * tx + y * tz - z * ty,
                vy + w * ty + z * tx - x * tz,
                vz + w * tz + x * ty - y * tx);
    }

    /** Angle between the body's local up axis and the world up axis, in degrees. */
    public double tiltDegrees() {
        Point3 localUp = rotate(Point3.UP);
        double cos = Math.max(-1.0, Math.min(1.0, localUp.y()));
        return Math.toDegrees(Math.acos(cos));
    }
}
