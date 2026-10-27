package dev.goshadows.createtrainssable.physics;

/** Curvature of the path sampled from a leading bogey's world positions. */
public final class CurveGeometry {

    private static final double MIN_AREA = 1.0e-6;

    private CurveGeometry() {
    }

    /**
     * Radius of the circle through three consecutive samples, in blocks.
     * Returns {@link Double#POSITIVE_INFINITY} for straight or degenerate samples.
     */
    public static double radius(Point3 first, Point3 second, Point3 third) {
        double ab = first.distanceTo(second);
        double bc = second.distanceTo(third);
        double ca = third.distanceTo(first);
        double area = second.sub(first).cross(third.sub(first)).length() * 0.5;
        if (area < MIN_AREA || ab < MIN_AREA || bc < MIN_AREA || ca < MIN_AREA) {
            return Double.POSITIVE_INFINITY;
        }
        return (ab * bc * ca) / (4.0 * area);
    }

    public static double curvature(Point3 first, Point3 second, Point3 third) {
        double radius = radius(first, second, third);
        return Double.isFinite(radius) ? 1.0 / radius : 0.0;
    }
}
