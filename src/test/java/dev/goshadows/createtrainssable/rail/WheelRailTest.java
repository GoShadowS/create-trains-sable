package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.config.TruckSettings;
import net.minecraft.world.phys.Vec3;
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
        public Vec3 point() {
            return Vec3.ZERO;
        }

        @Override
        public Vec3 tangent() {
            return new Vec3(1, 0, 0);
        }

        @Override
        public Vec3 up() {
            return new Vec3(0, 1, 0);
        }

        @Override
        public Vec3 lateral() {
            return tangent().cross(up()).normalize();
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
        WheelRail.Output output = WheelRail.solve(straightRail(), Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 0, 0), Vec3.ZERO, 0.0, true, 2, 1, 100.0, 1.0, SETTINGS);
        assertTrue(output.guiding(), "an axle in the middle of the rail must hold");
    }

    @Test
    void anAxleBeyondTheFlangeStopsGuiding() {
        Vec3 far = new Vec3(0, 0, SETTINGS.maxLateralError() + 0.5);
        WheelRail.Output output = WheelRail.solve(straightRail(), far, Vec3.ZERO,
                new Vec3(1, 0, 0), Vec3.ZERO, 0.0, true, 2, 1, 100.0, 10.0, SETTINGS);
        assertFalse(output.guiding());
        assertEquals(WheelRail.State.OFF_RAIL, output.state());
    }

    @Test
    void aFreshVehicleIsPulledOntoTheRail() {
        // Same offset as the failing case, but inside the window that follows assembly.
        Vec3 offset = new Vec3(0, 0, SETTINGS.maxLateralError() + 0.5);
        WheelRail.Output output = WheelRail.solve(straightRail(), offset, Vec3.ZERO,
                new Vec3(1, 0, 0), Vec3.ZERO, 0.0, true, 2, 1, 100.0, 0.1, SETTINGS);
        assertTrue(output.guiding(), "the attach window must accept an axle that is not settled yet");
        assertTrue(output.force().z < 0.0, "the flange must push towards the rail, not away");
    }

    @Test
    void aCurveTakenTooFastStopsGuiding() {
        double curvature = SETTINGS.curveLateralLimit();
        double tooFast = Math.sqrt(SETTINGS.curveLateralLimit() / curvature) + 1.0;
        WheelRail.Output output = WheelRail.solve(curvedRail(curvature), Vec3.ZERO,
                new Vec3(tooFast, 0, 0), new Vec3(1, 0, 0), Vec3.ZERO, tooFast, true, 2, 1, 100.0,
                10.0, SETTINGS);
        assertFalse(output.guiding());
        assertEquals(WheelRail.State.OVERSPEED, output.state());
    }

    @Test
    void tractionIsCappedByAdhesion() {
        // Demand far above what the wheel can transmit: 32 RPM is 1.6 m/s, the vehicle is still.
        WheelRail.Output output = WheelRail.solve(straightRail(), Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 0, 0), Vec3.ZERO, 30.0, true, 1, 1, 1000.0, 10.0, SETTINGS);
        double adhesion = SETTINGS.friction() * SETTINGS.gravity();
        assertTrue(output.force().x <= 1000.0 * adhesion + 1.0e-6,
                "the wheel cannot push harder than it grips");
    }

    @Test
    void anIdlerNeverPulls() {
        WheelRail.Output standing = WheelRail.solve(straightRail(), Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 0, 0), Vec3.ZERO, 30.0, false, 2, 1, 1000.0, 10.0, SETTINGS);
        assertTrue(standing.force().x <= 0.0, "a rolling truck may only resist motion");
    }

    @Test
    void targetSpeedFollowsTheShaftAndTheFacing() {
        assertEquals(1.6, WheelRail.targetSpeed(32.0f, 1.0, SETTINGS), 1.0e-9);
        assertEquals(-1.6, WheelRail.targetSpeed(32.0f, -1.0, SETTINGS), 1.0e-9);
        assertEquals(0.0, WheelRail.targetSpeed(0.0f, 1.0, SETTINGS), 1.0e-9);
    }

    @Test
    void alignmentTurnsTheShortWayRound() {
        Vec3 forward = new Vec3(0, 0, 1);
        Vec3 wanted = new Vec3(1, 0, 0);
        Vec3 delta = WheelRail.alignment(forward, wanted, Vec3.ZERO, SETTINGS);
        // Rotating (0,0,1) towards (1,0,0) is a positive rotation about +Y.
        assertTrue(delta.y > 0.0, "the body must turn towards the rail, not away from it");
        assertTrue(delta.length() <= SETTINGS.maxAngularAcceleration() + 1.0e-9);
    }
}
