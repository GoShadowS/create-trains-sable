package dev.goshadows.createtrainssable.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResyncEvaluatorTest {

    private static final Tuning TUNING = Tuning.defaults();

    private static Pose settled(double speed) {
        return new Pose(new Point3(10, 64, 10), Quat.IDENTITY,
                new Point3(speed, 0, 0), Point3.ZERO);
    }

    @Test
    void needsConsecutiveStableTicks() {
        ResyncEvaluator evaluator = new ResyncEvaluator();
        for (int i = 0; i < TUNING.rebinding().stableTicks() - 1; i++) {
            evaluator.observe(settled(0.01), 0.5, TUNING);
            assertFalse(evaluator.isReady(TUNING));
        }
        evaluator.observe(settled(0.01), 0.5, TUNING);
        assertTrue(evaluator.isReady(TUNING));
        assertEquals(TUNING.rebinding().stableTicks(), evaluator.stableTicks());
    }

    @Test
    void movementResetsTheCounter() {
        ResyncEvaluator evaluator = new ResyncEvaluator();
        evaluator.observe(settled(0.01), 0.5, TUNING);
        evaluator.observe(settled(4.0), 0.5, TUNING);
        assertEquals(0, evaluator.stableTicks());
    }

    @Test
    void aTiltedBodyCannotRebind() {
        ResyncEvaluator evaluator = new ResyncEvaluator();
        Quat flipped = new Quat(1.0, 0.0, 0.0, 0.0);
        Pose upsideDown = new Pose(new Point3(0, 64, 0), flipped, Point3.ZERO, Point3.ZERO);
        evaluator.observe(upsideDown, 0.1, TUNING);
        assertEquals(0, evaluator.stableTicks());
        assertEquals(180.0, flipped.tiltDegrees(), 1.0e-9);
    }

    @Test
    void distanceToTrackIsPartOfTheDecision() {
        ResyncEvaluator evaluator = new ResyncEvaluator();
        evaluator.observe(settled(0.01), 30.0, TUNING);
        assertFalse(evaluator.isReady(TUNING));
        assertEquals(30.0, evaluator.lastTrackDistance());
    }
}
