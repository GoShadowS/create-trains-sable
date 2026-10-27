package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MassPropertiesTest {

    private static final double EPS = 1.0e-9;

    @Test
    void twoEqualBlocksProduceAParallelAxisTensor() {
        List<WeightedBlock> blocks = List.of(
                new WeightedBlock(new Point3(0, 0, 0), 1.0),
                new WeightedBlock(new Point3(2, 0, 0), 1.0));
        MassProperties mass = MassProperties.fromBlocks(blocks, 0.0);

        assertEquals(2.0, mass.mass(), EPS);
        assertEquals(new Point3(1, 0, 0), mass.centerOfMass());
        assertEquals(0.0, mass.inertia()[0], EPS);
        assertEquals(2.0, mass.inertia()[4], EPS);
        assertEquals(2.0, mass.inertia()[8], EPS);
        assertEquals(2.0, mass.inertiaAlong(new Point3(0, 1, 0)), EPS);
    }

    @Test
    void intrinsicBlockInertiaIsAddedToEveryDiagonalTerm() {
        MassProperties single = MassProperties.fromBlocks(
                List.of(new WeightedBlock(new Point3(0, 0, 0), 6.0)),
                MassProperties.CUBE_INERTIA_FACTOR);
        assertEquals(1.0, single.inertia()[0], EPS);
        assertEquals(1.0, single.inertia()[4], EPS);
        assertEquals(1.0, single.inertia()[8], EPS);
    }

    @Test
    void offDiagonalTermsAreSymmetric() {
        MassProperties mass = MassProperties.fromBlocks(List.of(
                new WeightedBlock(new Point3(0, 0, 0), 1.0),
                new WeightedBlock(new Point3(1, 1, 1), 2.0),
                new WeightedBlock(new Point3(-2, 3, 0), 4.0)), 0.0);
        double[] tensor = mass.inertia();
        assertEquals(tensor[1], tensor[3], EPS);
        assertEquals(tensor[2], tensor[6], EPS);
        assertEquals(tensor[5], tensor[7], EPS);
    }

    @Test
    void kineticEnergyOfPureTranslationIsHalfMassSpeedSquared() {
        MassProperties mass = MassProperties.fromBlocks(
                List.of(new WeightedBlock(new Point3(0, 0, 0), 4.0)), 0.0);
        assertEquals(8.0, mass.kineticEnergy(2.0, Point3.ZERO), EPS);
    }

    @Test
    void emptyInputIsHandled() {
        assertSame(MassProperties.EMPTY, MassProperties.fromBlocks(List.of(), 0.0));
        assertTrue(MassProperties.EMPTY.isEmpty());
    }
}
