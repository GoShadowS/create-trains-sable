package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerailmentModelTest {

    private static final Tuning TUNING = Tuning.defaults();

    @Test
    void tighterCurvesLowerTheSafeSpeed() {
        assertTrue(DerailmentModel.safeSpeed(8.0, TUNING) < DerailmentModel.safeSpeed(24.0, TUNING));
        assertTrue(Double.isInfinite(DerailmentModel.safeSpeed(500.0, TUNING)));
    }

    @Test
    void overspeedOnACurveConvertsTheTrain() {
        double safe = DerailmentModel.safeSpeed(12.0, TUNING);
        DerailmentModel.Verdict verdict = DerailmentModel.evaluate(
                new DerailmentModel.Inputs(safe * 2.0, 12.0, 0.0, 0.0, false), TUNING);
        assertTrue(verdict.derail());
        assertEquals(DerailmentModel.Trigger.CURVE_OVERSPEED, verdict.trigger());
        assertEquals(2.0, verdict.margin(), 1.0e-9);
    }

    @Test
    void graceBandPreventsChatteringAtTheLimit() {
        double safe = DerailmentModel.safeSpeed(12.0, TUNING);
        assertFalse(DerailmentModel.evaluate(
                new DerailmentModel.Inputs(safe * 1.05, 12.0, 0.0, 0.0, false), TUNING).derail());
    }

    @Test
    void straightTrackNeverTripsTheCurveCriterion() {
        assertFalse(DerailmentModel.evaluate(
                new DerailmentModel.Inputs(40.0, Double.POSITIVE_INFINITY, 0.0, 0.0, false), TUNING).derail());
    }

    @Test
    void createCrashTakesPriorityOverCurveOverspeed() {
        DerailmentModel.Verdict verdict = DerailmentModel.evaluate(
                new DerailmentModel.Inputs(20.0, 5.0, 0.0, 0.0, true), TUNING);
        assertEquals(DerailmentModel.Trigger.CREATE_CRASH, verdict.trigger());
    }

    @Test
    void collisionAndHardBrakingAreIndependentTriggers() {
        assertEquals(DerailmentModel.Trigger.COLLISION, DerailmentModel.evaluate(
                new DerailmentModel.Inputs(1.0, 1000.0, 6.0, 0.0, false), TUNING).trigger());
        assertEquals(DerailmentModel.Trigger.HARD_BRAKING, DerailmentModel.evaluate(
                new DerailmentModel.Inputs(1.0, 1000.0, 0.0, 25.0, false), TUNING).trigger());
    }

    @Test
    void alwaysModeConvertsEvenOnStraightTrackAtLowSpeed() {
        Tuning always = new Tuning(
                new Tuning.General(true, Tuning.ConversionMode.ALWAYS, 10, 12000),
                TUNING.traction(), TUNING.derailment(), TUNING.rebinding(), TUNING.debug());
        assertEquals(DerailmentModel.Trigger.CONFIG_ALWAYS, DerailmentModel.evaluate(
                new DerailmentModel.Inputs(0.2, Double.POSITIVE_INFINITY, 0.0, 0.0, false), always).trigger());
    }
}
