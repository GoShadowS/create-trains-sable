package dev.goshadows.createtrainssable.bridge;

import dev.goshadows.createtrainssable.config.TruckSettings;
import dev.goshadows.createtrainssable.rail.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coupling is the only thing that makes a train of physical bodies a train, so its arithmetic is
 * tested on its own: a spring, a damper, a cap, and a sign convention that has to agree with the one
 * Create uses.
 */
class TrainCouplingsTest {

    private static final TruckSettings SETTINGS = TruckSettings.defaults();

    @Test
    void aCouplingAtItsRestLengthAsksForNothing() {
        TrainCouplings.Report report = TrainCouplings.report(
                Vec.ZERO, new Vec(4.0, 0, 0), 4.0, Vec.ZERO, Vec.ZERO, SETTINGS);

        assertEquals(4.0, report.distance(), 1e-9);
        assertEquals(0.0, report.extension(), 1e-9);
        assertEquals(0.0, report.acceleration(), 1e-9);
        assertFalse(report.stretched());
    }

    @Test
    void aStretchedCouplingPullsTheBodiesTogether() {
        // Half a block of extension, no relative motion: 6 /s^2 per block gives 3 m/s^2.
        TrainCouplings.Report report = TrainCouplings.report(
                Vec.ZERO, new Vec(4.5, 0, 0), 4.0, Vec.ZERO, Vec.ZERO, SETTINGS);

        assertEquals(0.5, report.extension(), 1e-9);
        assertEquals(3.0, report.acceleration(), 1e-9);
        assertTrue(report.stretched());
    }

    @Test
    void aCompressedCouplingPushesThemApart() {
        TrainCouplings.Report report = TrainCouplings.report(
                Vec.ZERO, new Vec(3.75, 0, 0), 4.0, Vec.ZERO, Vec.ZERO, SETTINGS);

        assertEquals(-0.25, report.extension(), 1e-9);
        assertEquals(-1.5, report.acceleration(), 1e-9);
    }

    @Test
    void separatingAddsDampingToTheSpring() {
        // 0.5 block of extension (3) plus 1 m/s separation at damping 4 gives 7 m/s^2.
        TrainCouplings.Report report = TrainCouplings.report(
                Vec.ZERO, new Vec(4.5, 0, 0), 4.0, Vec.ZERO, new Vec(1.0, 0, 0), SETTINGS);

        assertEquals(1.0, report.separating(), 1e-9);
        assertEquals(7.0, report.acceleration(), 1e-9);
    }

    @Test
    void closingRelievesTheSpring() {
        // 0.5 block of extension (3) minus 1 m/s of closing at damping 4 leaves -1 m/s^2.
        TrainCouplings.Report report = TrainCouplings.report(
                Vec.ZERO, new Vec(4.5, 0, 0), 4.0, new Vec(1.0, 0, 0), Vec.ZERO, SETTINGS);

        assertEquals(-1.0, report.separating(), 1e-9);
        assertEquals(-1.0, report.acceleration(), 1e-9);
    }

    @Test
    void theAccelerationIsCapped() {
        // Twenty blocks of extension would ask for 120 m/s^2; the coupling may transfer 14.
        assertEquals(14.0, TrainCouplings.acceleration(20.0, 0.0, SETTINGS), 1e-9);
        assertEquals(-14.0, TrainCouplings.acceleration(-20.0, 0.0, SETTINGS), 1e-9);
    }

    @Test
    void coincidentBodiesProduceNoForce() {
        TrainCouplings.Report report = TrainCouplings.report(
                Vec.ZERO, Vec.ZERO, 1.0, Vec.ZERO, Vec.ZERO, SETTINGS);

        assertEquals(0.0, report.acceleration(), 1e-9);
    }
}
