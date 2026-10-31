package dev.goshadows.createtrainssable.rail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruckSettingsTest {

    @Test
    void defaultsSurviveValidationUnchanged() {
        TruckSettings defaults = TruckSettings.defaults();
        assertEquals(defaults, defaults.validated());
    }

    @Test
    void garbageIsClampedInsteadOfReachingThePhysicsEngine() {
        TruckSettings broken = new TruckSettings(
                Double.NaN, -1.0, -1.0, -1.0, -1.0, 0.0, -1.0, -1.0, 0.0,
                -1.0, -1.0, -1.0, 0.0, 0, 0.0, -1.0, -1.0, -1.0, -1.0);

        TruckSettings fixed = broken.validated();

        assertEquals(0.0, fixed.speedPerRpm());
        assertEquals(0.0, fixed.guideStiffness());
        assertEquals(0.1, fixed.maxGuideAcceleration());
        assertEquals(0.05, fixed.maxLateralError());
        assertEquals(2.0, fixed.scanRadius());
        assertEquals(1, fixed.trackRecheckTicks());
        assertEquals(0.5, fixed.couplingRange());
        assertEquals(0.0, fixed.gravity());
    }

    @Test
    void theDefaultTopSpeedOfAFullSpeedShaftIsUsable() {
        TruckSettings settings = TruckSettings.defaults();
        // A full speed shaft in Create turns at 256 RPM.
        double speed = 256 * settings.speedPerRpm();
        assertTrue(speed > 10.0 && speed < 20.0, "256 RPM gave " + speed + " m/s");
    }
}
