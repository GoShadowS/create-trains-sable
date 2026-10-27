package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TractionModelTest {

    private static final Tuning TUNING = Tuning.defaults();
    private static final double EPS = 1.0e-9;

    @Test
    void fullThrottleTransmitsTheWholeAdhesionLimit() {
        TractionModel.Result result = TractionModel.evaluate(
                new TractionModel.Demand(1.0, 0.0, 5.0, 10.0), TUNING);
        double expected = 0.45 * 10.0 * 9.81;
        assertEquals(expected, result.force(), EPS);
        assertFalse(result.slipping());
    }

    @Test
    void excessiveBrakeDemandCausesWheelSlip() {
        TractionModel.Result result = TractionModel.evaluate(
                new TractionModel.Demand(0.0, 1.0, 8.0, 10.0),
                new Tuning(TUNING.general(),
                        new Tuning.Traction(0.5, 0.1, 0.45, 0.02, 3.0, 0.18, 9.81),
                        TUNING.derailment(), TUNING.rebinding(), TUNING.debug()));
        assertTrue(result.slipping());
        assertTrue(result.slipRatio() > 0.18);
        assertTrue(result.force() < 0.0);
        assertTrue(Math.abs(result.force()) <= 0.45 * 10.0 * 9.81 + EPS);
    }

    @Test
    void brakingDoesNotReverseANearlyStationaryBody() {
        TractionModel.Result result = TractionModel.evaluate(
                new TractionModel.Demand(0.0, 1.0, 0.01, 10.0), TUNING);
        assertEquals(0.0, result.force(), EPS);
    }

    @Test
    void rollingResistanceOpposesMotionAndVanishesAtRest() {
        assertTrue(TractionModel.rollingResistanceForce(10.0, 5.0, TUNING) < 0.0);
        assertTrue(TractionModel.rollingResistanceForce(10.0, -5.0, TUNING) > 0.0);
        assertEquals(0.0, TractionModel.rollingResistanceForce(10.0, 0.0, TUNING), EPS);
    }
}
