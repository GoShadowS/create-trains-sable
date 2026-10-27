package dev.goshadows.createtrainssable.derailment;

import dev.goshadows.createtrainssable.physics.CurveGeometry;
import dev.goshadows.createtrainssable.physics.Point3;

/** Sliding window over the three most recent leading-bogey samples. */
public final class CurveWindow {

    public record Observation(double radius, double deceleration, double speedDrop) {
    }

    private final Point3[] samples = new Point3[3];
    private int count;
    private double lastSpeed;
    private int lastTick = -1;

    public Observation observe(Point3 point, double speedMetresPerSecond, int tick) {
        double speedDrop = Math.max(0.0, lastSpeed - speedMetresPerSecond);
        double deceleration = 0.0;
        if (lastTick >= 0 && tick > lastTick) {
            deceleration = speedDrop / ((tick - lastTick) / 20.0);
        }
        lastSpeed = speedMetresPerSecond;
        lastTick = tick;

        samples[0] = samples[1];
        samples[1] = samples[2];
        samples[2] = point;
        count = Math.min(3, count + 1);
        double radius = count < 3 ? Double.POSITIVE_INFINITY
                : CurveGeometry.radius(samples[0], samples[1], samples[2]);
        return new Observation(radius, deceleration, speedDrop);
    }
}
