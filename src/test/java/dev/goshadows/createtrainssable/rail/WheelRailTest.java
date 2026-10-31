package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.config.TruckSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contact is the only place where the mod decides whether a vehicle is on the rail, so it is
 * tested directly: as arithmetic, with no game running.
 *
 * <p>The rail used here is straight and level, pointing along +X, which makes every expected value
 * something that can be checked by hand.</p>
 */
class WheelRailTest {

    private static final TruckSettings SETTINGS = TruckSettings.defaults();

    /** A rail along +X at the origin, level, with the given curvature. */
    private record TestRail(double curvature) implements RailFrame {

        @Override
        public Vec point() {
            return Vec.ZERO;
        }

        @Override
        public Vec tangent() {
            return new Vec(1, 0, 0);
        }

        @Override
        public Vec up() {
            return new Vec(0, 1, 0);
        }

        @Override
        public Vec lateral() {
            return tangent().cross(up()).normalized();
        }
    }

    private static RailFrame straightRail() {
        return new TestRail(0.0);
    }

    private static RailFrame curvedRail(double curvature) {
        return new TestRail(curvature);
    }

    @Test
    void anAxleOnTheRailIsGuided() {
        WheelRail.Output output = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO,
                new Vec(1, 0, 0), Vec.ZERO, 0.0, true, 2, 1, 100.0, SETTINGS.gravity(), 1.0,
                SETTINGS);
        assertTrue(output.guiding(), "an axle in the middle of the rail must hold");
    }

    @Test
    void anAxleBeyondTheFlangeStopsGuiding() {
        Vec far = new Vec(0, 0, SETTINGS.maxLateralError() + 0.5);
        WheelRail.Output output = WheelRail.solve(straightRail(), far, Vec.ZERO,
                new Vec(1, 0, 0), Vec.ZERO, 0.0, true, 2, 1, 100.0, SETTINGS.gravity(), 10.0,
                SETTINGS);
        assertFalse(output.guiding());
        assertEquals(WheelRail.State.OFF_RAIL, output.state());
    }

    @Test
    void aFreshVehicleIsPulledOntoTheRail() {
        // Same offset as the failing case, but inside the window that follows assembly.
        Vec offset = new Vec(0, 0, SETTINGS.maxLateralError() + 0.5);
        WheelRail.Output output = WheelRail.solve(straightRail(), offset, Vec.ZERO,
                new Vec(1, 0, 0), Vec.ZERO, 0.0, true, 2, 1, 100.0, SETTINGS.gravity(), 0.1,
                SETTINGS);
        assertTrue(output.guiding(), "the attach window must accept an axle that is not settled yet");
        assertTrue(output.force().z() < 0.0, "the flange must push towards the rail, not away");
    }

    @Test
    void aCurveTakenTooFastStopsGuiding() {
        double curvature = SETTINGS.curveLateralLimit();
        double tooFast = Math.sqrt(SETTINGS.curveLateralLimit() / curvature) + 1.0;
        WheelRail.Output output = WheelRail.solve(curvedRail(curvature), Vec.ZERO,
                new Vec(tooFast, 0, 0), new Vec(1, 0, 0), Vec.ZERO, tooFast, true, 2, 1, 100.0,
                SETTINGS.gravity(), 10.0, SETTINGS);
        assertFalse(output.guiding());
        assertEquals(WheelRail.State.OVERSPEED, output.state());
    }

    @Test
    void tractionIsCappedByAdhesion() {
        // Demand far above what the wheel can transmit: 32 RPM is 1.6 m/s, the vehicle is still.
        WheelRail.Output output = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO,
                new Vec(1, 0, 0), Vec.ZERO, 30.0, true, 1, 1, 1000.0, SETTINGS.gravity(),
                10.0, SETTINGS);
        double adhesion = SETTINGS.friction() * SETTINGS.gravity();
        assertTrue(output.force().x() <= 1000.0 * adhesion + 1.0e-6,
                "the wheel cannot push harder than it grips");
    }

    @Test
    void anIdlerNeverPulls() {
        WheelRail.Output standing = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO,
                new Vec(1, 0, 0), Vec.ZERO, 30.0, false, 2, 1, 1000.0, SETTINGS.gravity(),
                10.0, SETTINGS);
        assertTrue(standing.force().x() <= 0.0, "a rolling truck may only resist motion");
    }

    @Test
    void everyAxleCarriesAnEqualShareOfTheWeight() {
        // Each axle reports the force it must apply; the sum over the vehicle is what holds it up.
        double mass = 400.0;
        for (int trucks : new int[] {1, 2, 4, 6}) {
            WheelRail.Output output = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO,
                    new Vec(1, 0, 0), Vec.ZERO, 0.0, false, trucks, 0, mass, SETTINGS.gravity(),
                    10.0, SETTINGS);
            double expected = mass * SETTINGS.gravity();
            assertEquals(expected, output.force().y() * trucks, expected * 1.0e-6,
                    trucks + " axles must carry the weight between them, not once each");
        }
    }

    @Test
    void supportIsSharedEvenlyWhenOnlySomeAxlesAreDriven() {
        // The harness found this one: with one driven axle on a two-axle vehicle the driven axle
        // was scaling its share by the driven count, so it pushed twice its half of the weight and
        // the body climbed off the rail.
        double mass = 400.0;
        WheelRail.Output driven = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO,
                new Vec(1, 0, 0), Vec.ZERO, 0.0, true, 2, 1, mass, SETTINGS.gravity(), 10.0,
                SETTINGS);
        assertEquals(mass * SETTINGS.gravity() / 2.0, driven.force().y(), mass * 1.0e-6,
                "a driven axle carries half the weight, not all of it");
    }

    @Test
    void gripIsTheSameOnOneAxleAsOnSix() {
        // A wheel grips in proportion to the weight it carries and the mass it pushes; both are
        // shared by the axle count, so the acceleration the vehicle can reach is not.
        double mass = 1000.0;
        double expected = mass * Math.min(SETTINGS.traction(), SETTINGS.friction() * SETTINGS.gravity());
        for (int driven : new int[] {1, 2, 4}) {
            WheelRail.Output output = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO,
                    new Vec(1, 0, 0), Vec.ZERO, 30.0, true, driven, driven, mass,
                    SETTINGS.gravity(), 10.0, SETTINGS);
            assertEquals(expected, output.force().x() * driven, expected * 1.0e-6,
                    "traction must not depend on how many axles are driven");
        }
    }

    @Test
    void rollingResistanceIsAPropertyOfTheVehicle() {
        double mass = 600.0;
        double totalOne = 0.0;
        double totalFour = 0.0;
        int trucks = 1;
        for (int pass = 0; pass < 2; pass++) {
            WheelRail.Output output = WheelRail.solve(straightRail(), Vec.ZERO,
                    new Vec(5, 0, 0), new Vec(1, 0, 0), Vec.ZERO, 0.0, false, trucks, 0, mass,
                    SETTINGS.gravity(), 10.0, SETTINGS);
            if (pass == 0) {
                totalOne = output.force().x();
                trucks = 4;
            } else {
                totalFour = output.force().x() * 4;
            }
        }
        assertEquals(totalOne, totalFour, Math.abs(totalOne) * 1.0e-6,
                "an idler must slow the vehicle by the same amount however many idlers there are");
        assertTrue(totalOne < 0.0, "an idler only ever takes energy out of the vehicle");
    }

    @Test
    void steeringIsSharedBetweenAxles() {
        Vec forward = new Vec(0, 0, 1);
        double one = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO, forward, Vec.ZERO, 0.0,
                false, 1, 0, 100.0, SETTINGS.gravity(), 10.0, SETTINGS).angularAcceleration().length();
        double four = WheelRail.solve(straightRail(), Vec.ZERO, Vec.ZERO, forward, Vec.ZERO, 0.0,
                false, 4, 0, 100.0, SETTINGS.gravity(), 10.0, SETTINGS)
                .angularAcceleration().length() * 4.0;
        assertEquals(one, four, one * 1.0e-6, "four axles must turn the body once, not four times");
    }

    @Test
    void targetSpeedFollowsTheShaftAndTheFacing() {
        assertEquals(1.6, WheelRail.targetSpeed(32.0f, 1.0, SETTINGS), 1.0e-9);
        assertEquals(-1.6, WheelRail.targetSpeed(32.0f, -1.0, SETTINGS), 1.0e-9);
        assertEquals(0.0, WheelRail.targetSpeed(0.0f, 1.0, SETTINGS), 1.0e-9);
    }

    @Test
    void alignmentTurnsTheShortWayRound() {
        Vec forward = new Vec(0, 0, 1);
        Vec wanted = new Vec(1, 0, 0);
        Vec delta = WheelRail.alignment(forward, wanted, Vec.ZERO, SETTINGS);
        // Rotating (0,0,1) towards (1,0,0) is a positive rotation about +Y.
        assertTrue(delta.y() > 0.0, "the body must turn towards the rail, not away from it");
        assertTrue(delta.length() <= SETTINGS.maxAngularAcceleration() + 1.0e-9);
    }
}
