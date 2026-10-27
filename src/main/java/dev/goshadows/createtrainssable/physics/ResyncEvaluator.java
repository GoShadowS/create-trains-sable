package dev.goshadows.createtrainssable.physics;

/**
 * Hysteresis for returning a wreck to the TrackGraph. A single quiet tick is not enough:
 * Rapier can report a near-zero velocity while a body is still balanced on a rail.
 */
public final class ResyncEvaluator {

    private int stableTicks;
    private double lastTrackDistance = Double.POSITIVE_INFINITY;
    private double lastSpeed;

    public boolean observe(Pose pose, double distanceToTrack, Tuning tuning) {
        Tuning.Rebinding r = tuning.rebinding();
        boolean stable = pose.speed() <= r.maxSpeed()
                && pose.angularSpeed() <= r.maxAngularSpeed()
                && pose.tiltDegrees() <= r.maxTiltDegrees()
                && distanceToTrack <= r.maxTrackDistance();
        stableTicks = stable ? stableTicks + 1 : 0;
        lastTrackDistance = distanceToTrack;
        lastSpeed = pose.speed();
        return stable;
    }

    public boolean isReady(Tuning tuning) {
        return stableTicks >= tuning.rebinding().stableTicks();
    }

    public int stableTicks() {
        return stableTicks;
    }

    public double lastTrackDistance() {
        return lastTrackDistance;
    }

    public double lastSpeed() {
        return lastSpeed;
    }

    public void reset() {
        stableTicks = 0;
    }
}
