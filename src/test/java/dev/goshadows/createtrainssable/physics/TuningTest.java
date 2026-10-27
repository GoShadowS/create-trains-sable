package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuningTest {

    @Test
    void defaultsSurviveValidationUnchanged() {
        Tuning defaults = Tuning.defaults();
        assertEquals(defaults, defaults.validated());
    }

    @Test
    void invalidNumbersAreClampedInsteadOfPoisoningTheSimulation() {
        Tuning broken = new Tuning(
                new Tuning.General(true, null, 0, 1),
                new Tuning.Traction(Double.NaN, -5.0, 0.0, -1.0, 100.0, 0.0, Double.POSITIVE_INFINITY),
                new Tuning.Derailment(-1.0, 0.0, 0.0, 0.0, -2.0, 0.5, -1.0, -3.0),
                new Tuning.Rebinding(-1.0, -1.0, -1.0, -1.0, 0),
                new Tuning.Debug(false));

        Tuning fixed = broken.validated();
        assertEquals(Tuning.ConversionMode.THRESHOLD, fixed.general().mode());
        assertEquals(1, fixed.general().maxActiveTrains());
        assertEquals(0.01, fixed.traction().blockMassScale());
        assertEquals(0.1, fixed.traction().gravity());
        assertEquals(0.1, fixed.derailment().cantReliefFactor());
        assertEquals(1.0, fixed.derailment().overspeedGrace());
        assertEquals(0.0, fixed.rebinding().maxSpeed());
        assertEquals(1, fixed.rebinding().stableTicks());
        assertTrue(Double.isFinite(DerailmentModel.safeSpeed(0.5, fixed)));
    }

    @Test
    void disabledModeNeverDerails() {
        Tuning tuning = new Tuning(
                new Tuning.General(true, Tuning.ConversionMode.DISABLED, 10, 12000),
                Tuning.defaults().traction(),
                Tuning.defaults().derailment(),
                Tuning.defaults().rebinding(),
                Tuning.defaults().debug());
        DerailmentModel.Inputs inputs = new DerailmentModel.Inputs(40.0, 2.0, 0.0, 0.0, false);
        assertEquals(DerailmentModel.Trigger.NONE, DerailmentModel.evaluate(inputs, tuning).trigger());
    }
}
