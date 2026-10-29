package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuatTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void identityAxesGiveTheIdentityRotation() {
        Quat quat = Quat.fromAxes(new Point3(1.0, 0.0, 0.0),
                new Point3(0.0, 1.0, 0.0), new Point3(0.0, 0.0, 1.0));

        assertEquals(1.0, quat.w(), EPSILON);
        assertEquals(0.0, quat.x(), EPSILON);
        assertEquals(0.0, quat.y(), EPSILON);
        assertEquals(0.0, quat.z(), EPSILON);
    }

    @Test
    void quarterTurnAroundYRotatesTheLocalXAxisAsGiven() {
        // A carriage turned 90 degrees: local +X now points at world -Z, local +Z at world +X.
        Quat quat = Quat.fromAxes(new Point3(0.0, 0.0, -1.0),
                new Point3(0.0, 1.0, 0.0), new Point3(1.0, 0.0, 0.0));

        Point3 rotated = quat.rotate(new Point3(1.0, 0.0, 0.0));

        assertEquals(0.0, rotated.x(), EPSILON);
        assertEquals(0.0, rotated.y(), EPSILON);
        assertEquals(-1.0, rotated.z(), EPSILON);
    }

    @Test
    void axesRoundTripThroughTheQuaternion() {
        Point3 xAxis = new Point3(0.0, Math.sqrt(0.5), Math.sqrt(0.5));
        Point3 yAxis = new Point3(1.0, 0.0, 0.0);
        Point3 zAxis = xAxis.cross(yAxis);

        Quat quat = Quat.fromAxes(xAxis, yAxis, zAxis);

        assertVectorEquals(xAxis, quat.rotate(new Point3(1.0, 0.0, 0.0)));
        assertVectorEquals(yAxis, quat.rotate(new Point3(0.0, 1.0, 0.0)));
        assertVectorEquals(zAxis, quat.rotate(new Point3(0.0, 0.0, 1.0)));
    }

    @Test
    void halfTurnStaysStableAndUprightIsTiltedOnItsSide() {
        // 180 degrees around X: local up now points down, so the tilt is a half turn.
        Quat quat = Quat.fromAxes(new Point3(1.0, 0.0, 0.0),
                new Point3(0.0, -1.0, 0.0), new Point3(0.0, 0.0, -1.0));

        assertEquals(180.0, quat.tiltDegrees(), 1.0e-6);
        assertEquals(1.0, quat.x() * quat.x() + quat.y() * quat.y()
                + quat.z() * quat.z() + quat.w() * quat.w(), EPSILON);
    }

    private static void assertVectorEquals(Point3 expected, Point3 actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }
}
