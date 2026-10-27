package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveGeometryTest {

    @Test
    void straightSamplesHaveNoCurvature() {
        double radius = CurveGeometry.radius(
                new Point3(0, 0, 0), new Point3(1, 0, 0), new Point3(2, 0, 0));
        assertTrue(Double.isInfinite(radius));
        assertEquals(0.0, CurveGeometry.curvature(
                new Point3(0, 0, 0), new Point3(1, 0, 0), new Point3(2, 0, 0)));
    }

    @Test
    void threePointsOnAnArcReturnItsRadius() {
        double radius = CurveGeometry.radius(
                new Point3(0, 0, 0), new Point3(1, 0, 1), new Point3(2, 0, 0));
        assertEquals(1.0, radius, 1.0e-9);
    }

    @Test
    void duplicateSamplesAreDegenerate() {
        assertTrue(Double.isInfinite(CurveGeometry.radius(
                new Point3(3, 1, 2), new Point3(3, 1, 2), new Point3(3, 1, 2))));
    }
}
