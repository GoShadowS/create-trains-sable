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

    /**
     * Rotation whose matrix has the given world-space images of the local axes as its columns.
     *
     * <p>Uses the trace method with a fallback to the largest diagonal element, so rotations near
     * 180 degrees stay numerically stable. Kept here, next to {@link #rotate(Point3)}, so the
     * conversion from Create's yaw/pitch/roll basis needs no JOML type on the boundary.</p>
     */
    public static Quat fromAxes(Point3 xAxis, Point3 yAxis, Point3 zAxis) {
        double m00 = xAxis.x();
        double m10 = xAxis.y();
        double m20 = xAxis.z();
        double m01 = yAxis.x();
        double m11 = yAxis.y();
        double m21 = yAxis.z();
        double m02 = zAxis.x();
        double m12 = zAxis.y();
        double m22 = zAxis.z();

        double trace = m00 + m11 + m22;
        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            return new Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s);
        }
        if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
            return new Quat(0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s);
        }
        if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
            return new Quat((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s);
        }
        double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
        return new Quat((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s);
    }

    /** Angle between the body's local up axis and the world up axis, in degrees. */
    public double tiltDegrees() {
        Point3 localUp = rotate(Point3.UP);
        double cos = Math.max(-1.0, Math.min(1.0, localUp.y()));
        return Math.toDegrees(Math.acos(cos));
    }
}
