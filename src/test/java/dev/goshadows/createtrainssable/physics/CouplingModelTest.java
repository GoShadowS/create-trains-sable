package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplingModelTest {

    private static final Tuning TUNING = Tuning.defaults();

    @Test
    void aCouplerAtRestLengthDoesNothing() {
        Point3 a = new Point3(0, 64, 0);
        Point3 b = new Point3(0, 64, 4);
        CouplingModel.Pair pair = CouplingModel.solve(a, Point3.ZERO, 1000.0,
                b, Point3.ZERO, 1000.0, 4.0, TUNING);

        assertEquals(0.0, pair.force(), 1.0e-9);
        assertEquals(0.0, pair.onFirst().length(), 1.0e-9);
        assertEquals(0.0, pair.onSecond().length(), 1.0e-9);
    }

    @Test
    void aStretchedCouplerPullsBothCarriagesTogether() {
        Point3 a = new Point3(0, 64, 0);
        Point3 b = new Point3(0, 64, 6);
        CouplingModel.Pair pair = CouplingModel.solve(a, Point3.ZERO, 1000.0,
                b, Point3.ZERO, 1000.0, 4.0, TUNING);

        assertTrue(pair.extension() > 0.0);
        // The leading carriage is pulled forward, the trailing one is pulled back.
        assertTrue(pair.onFirst().z() > 0.0);
        assertTrue(pair.onSecond().z() < 0.0);
        assertEquals(0.0, pair.onFirst().add(pair.onSecond()).length(), 1.0e-9,
                "a coupler cannot invent momentum");
    }

    @Test
    void aCompressedCouplerPushesTheCarriagesApart() {
        Point3 a = new Point3(0, 64, 0);
        Point3 b = new Point3(0, 64, 2);
        CouplingModel.Pair pair = CouplingModel.solve(a, Point3.ZERO, 1000.0,
                b, Point3.ZERO, 1000.0, 4.0, TUNING);

        assertTrue(pair.extension() < 0.0);
        assertTrue(pair.onFirst().z() < 0.0);
        assertTrue(pair.onSecond().z() > 0.0);
    }

    @Test
    void theCouplerIsRated() {
        Point3 a = new Point3(0, 64, 0);
        Point3 b = new Point3(0, 64, 40);
        CouplingModel.Pair pair = CouplingModel.solve(a, Point3.ZERO, 1000.0,
                b, Point3.ZERO, 1000.0, 4.0, TUNING);

        double reducedMass = 500.0;
        double limit = TUNING.rails().couplingMaxAcceleration() * reducedMass;
        assertEquals(limit, pair.force(), 1.0e-6);
    }

    @Test
    void couplingCanBeDisabled() {
        Tuning noCoupling = new Tuning(
                TUNING.general(), TUNING.traction(), TUNING.derailment(),
                new Tuning.Rails(true, 10.0, 4.0, 24.0, 5.0, 2.0, 12.0, 0.35, false,
                        8.0, 4.0, 20.0, 1.5, 10),
                TUNING.rebinding(), TUNING.debug());

        CouplingModel.Pair pair = CouplingModel.solve(new Point3(0, 64, 0), Point3.ZERO, 1000.0,
                new Point3(0, 64, 40), Point3.ZERO, 1000.0, 4.0, noCoupling);

        assertEquals(0.0, pair.force(), 1.0e-9);
    }
}
