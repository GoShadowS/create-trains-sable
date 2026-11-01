package dev.goshadows.createtrainssable.rail;

/**
 * A plain three-dimensional vector.
 *
 * <p>The wheel/rail contact is arithmetic on vectors and nothing else, so it uses this type instead
 * of a game class. That keeps {@link WheelRail} free of Minecraft, which is what makes it testable
 * without starting a game.</p>
 */
public record Vec(double x, double y, double z) {

    public static final Vec ZERO = new Vec(0.0, 0.0, 0.0);
    public static final Vec UP = new Vec(0.0, 1.0, 0.0);

    public Vec add(Vec other) {
        return new Vec(x + other.x, y + other.y, z + other.z);
    }

    public Vec sub(Vec other) {
        return new Vec(x - other.x, y - other.y, z - other.z);
    }

    public Vec scale(double factor) {
        return new Vec(x * factor, y * factor, z * factor);
    }

    public double dot(Vec other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec cross(Vec other) {
        return new Vec(y * other.z - z * other.y, z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    /** Unit vector, or {@code ZERO} when this vector has no direction. */
    public Vec normalized() {
        double length = length();
        return length < 1.0e-9 ? ZERO : scale(1.0 / length);
    }

    /** Copies a game vector into the arithmetic type. */
    public static Vec of(net.minecraft.world.phys.Vec3 vector) {
        return new Vec(vector.x, vector.y, vector.z);
    }
}
