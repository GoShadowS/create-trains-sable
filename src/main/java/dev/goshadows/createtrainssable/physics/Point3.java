package dev.goshadows.createtrainssable.physics;

/** A point or vector in world space. Coordinates are blocks unless a method says otherwise. */
public record Point3(double x, double y, double z) {

    public static final Point3 ZERO = new Point3(0.0, 0.0, 0.0);
    public static final Point3 UP = new Point3(0.0, 1.0, 0.0);

    public Point3 add(Point3 other) {
        return new Point3(x + other.x, y + other.y, z + other.z);
    }

    public Point3 sub(Point3 other) {
        return new Point3(x - other.x, y - other.y, z - other.z);
    }

    public Point3 scale(double factor) {
        return new Point3(x * factor, y * factor, z * factor);
    }

    public double dot(Point3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Point3 cross(Point3 other) {
        return new Point3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    public double distanceTo(Point3 other) {
        return sub(other).length();
    }

    public Point3 normalized() {
        double length = length();
        return length < 1.0e-9 ? ZERO : scale(1.0 / length);
    }
}
