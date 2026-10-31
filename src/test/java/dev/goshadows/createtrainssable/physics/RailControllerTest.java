package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailControllerTest {

    private static final Tuning TUNING = Tuning.defaults();
    private static final double MASS = 1200.0;
    private static final Point3 INERTIA = new Point3(4000.0, 6000.0, 4000.0);

    private static Pose at(Point3 position) {
        return new Pose(position, Quat.IDENTITY, Point3.ZERO, Point3.ZERO);
    }

    @Test
    void aTrainSittingOnItsRailsOnlyHasGravityToCancel() {
        Pose target = at(new Point3(0, 64, 0));
        RailController.Output output =
                RailController.control(target, target, MASS, INERTIA, TUNING);

        assertEquals(0.0, output.error(), 1.0e-9);
        assertEquals(0.0, output.force().x(), 1.0e-9);
        assertEquals(0.0, output.force().z(), 1.0e-9);
        // Feed-forward gravity: mass times g upward, not a sag of g / stiffness.
        assertEquals(MASS * TUNING.traction().gravity(), output.force().y(), 1.0e-6);
        assertFalse(output.saturated());
    }

    @Test
    void aDisplacedCarriageIsPulledBackTowardsTheRails() {
        Pose current = at(new Point3(0.5, 64, 0));
        Pose target = at(new Point3(0, 64, 0));

        RailController.Output output = RailController.control(current, target, MASS, INERTIA, TUNING);

        assertEquals(0.5, output.error(), 1.0e-9);
        assertTrue(output.force().x() < 0.0, "force must point back towards the target");
        assertEquals(MASS * TUNING.traction().gravity(), output.force().y(), 1.0e-6);
        assertEquals(-MASS * TUNING.rails().positionGain() * 0.5, output.force().x(), 1.0e-6);
    }

    @Test
    void theGripOfTheRailsIsLimitedAndReported() {
        Pose current = at(new Point3(50.0, 64, 0));
        Pose target = at(new Point3(0, 64, 0));

        RailController.Output output = RailController.control(current, target, MASS, INERTIA, TUNING);

        assertTrue(output.saturated());
        double acceleration = output.force().length() / MASS;
        assertTrue(acceleration <= TUNING.rails().maxAcceleration() + 1.0e-6,
                "the rails must not exceed their own grip, was " + acceleration);
    }

    @Test
    void aHeavierTrainNeedsProportionallyMoreForceAndBehavesTheSame() {
        Pose current = at(new Point3(0.2, 64, 0));
        Pose target = at(new Point3(0, 64, 0));

        RailController.Output light = RailController.control(current, target, 600.0, INERTIA, TUNING);
        RailController.Output heavy = RailController.control(current, target, 6000.0, INERTIA, TUNING);

        assertEquals(light.force().length() * 10.0, heavy.force().length(), 1.0e-6);
    }

    @Test
    void aRotatedCarriageGetsATorqueBackToTheTrackFrame() {
        Pose current = new Pose(new Point3(0, 64, 0),
                Quat.fromAxes(new Point3(1, 0, 0), new Point3(0, 0, -1), new Point3(0, 1, 0)),
                Point3.ZERO, Point3.ZERO);
        Pose target = at(new Point3(0, 64, 0));

        RailController.Output output = RailController.control(current, target, MASS, INERTIA, TUNING);

        assertEquals(Math.PI / 2.0, output.angleError(), 1.0e-6);
        assertTrue(output.torque().length() > 0.0, "the wheels must twist the carriage back");
        assertTrue(output.force().length() < MASS * TUNING.rails().maxAcceleration() + 1.0e-6);
    }

    @Test
    void aBodyAlreadyMovingWithTheTrainIsNotAccelerated() {
        Pose target = new Pose(new Point3(0, 64, 0), Quat.IDENTITY,
                new Point3(5, 0, 0), Point3.ZERO);
        Pose current = new Pose(new Point3(0, 64, 0), Quat.IDENTITY,
                new Point3(5, 0, 0), Point3.ZERO);

        RailController.Output output = RailController.control(current, target, MASS, INERTIA, TUNING);

        assertEquals(0.0, output.force().x(), 1.0e-9);
        assertEquals(0.0, output.torque().length(), 1.0e-9);
    }
}
