package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.config.TruckSettings;
/**
 * The wheel/rail contact, as pure arithmetic.
 *
 * <p>An axle carries one wheel set. The contact has three jobs and no state of its own:</p>
 *
 * <ol>
 *   <li><b>Hold the axle on the rail.</b> A spring-damper in the plane perpendicular to the rail
 *       pushes the axle back onto the rail head. Its strength is the flange, and it is clamped: the
 *       rail can only push so hard before the flange climbs over it.</li>
 *   <li><b>Pull or brake along the rail.</b> A driven axle tries to reach the speed its shaft asks
 *       for; an idler only rolls. Both are capped by the adhesion of the wheel, which is what makes
 *       a heavy train accelerate slowly instead of magically.</li>
 *   <li><b>Turn the body, indirectly.</b> The contact applies its force at the axle, not at the centre
 *       of mass, so two axles that are pushed sideways in opposite directions yaw the structure the
 *       way a bogie does. Nothing writes an angular velocity: an earlier version did, and the harness
 *       measured the body spinning a half turn and then tumbling.</li>
 * </ol>
 *
 * <p>Everything is returned as an acceleration and a force, never as a velocity or a position: the
 * caller turns it into an impulse, and the physics engine decides what the body actually does. That
 * separation is the whole difference between a train that is simulated and a train that is animated
 * along a spline.</p>
 */
public final class WheelRail {

    /** Why the contact gave up, for logging; {@link #GUIDING} means it is holding. */
    public enum State {
        GUIDING,
        /** No rail within reach. */
        NO_TRACK,
        /** The axle is further from the rail than the flange allows. */
        OFF_RAIL,
        /** The curve cannot hold the vehicle at this speed. */
        OVERSPEED
    }

    /**
     * One substep of one axle.
     *
     * @param force              world force in newtons, to be applied at the axle
     * @param state              whether the contact is holding the vehicle
     * @param lateralError       measured lateral error in blocks, for logging
     * @param verticalError      measured vertical error in blocks, for logging
     * @param longitudinalError  difference between the commanded and the actual speed, in m/s
     */
    public record Output(Vec force, State state, double lateralError, double verticalError,
                         double longitudinalError) {

        public boolean guiding() {
            return state == State.GUIDING;
        }
    }

    private static final Output IDLE = new Output(Vec.ZERO, State.NO_TRACK, 0.0, 0.0, 0.0);

    private WheelRail() {
    }

    /**
     * @param track        the rail under the axle, or {@code null} when there is none
     * @param axle         world position of the axle
     * @param velocity     world velocity of the body at the axle
     * @param targetSpeed  speed the shaft asks for, signed along the rail
     * @param driven       true for a traction truck
     * @param truckCount   trucks on the vehicle, for sharing the weight
     * @param drivenCount  driven trucks on the vehicle, for sharing the demand
     * @param mass         mass of the whole vehicle, in Sable's mass units
     * @param gravity      gravity of the dimension the vehicle is in, in m/s^2
     * @param secondsSinceAssembly how long ago the vehicle was assembled, for the attach window
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Output solve(RailFrame track, Vec axle, Vec velocity,
                               double targetSpeed, boolean driven, int truckCount, int drivenCount,
                               double mass, double gravity, double secondsSinceAssembly,
                               TruckSettings settings) {
        if (track == null) {
            return IDLE;
        }
        Vec offset = axle.sub(track.point());
        double lateralError = offset.dot(track.lateral());
        double verticalError = offset.dot(track.up());
        double lateralSpeed = velocity.dot(track.lateral());
        double verticalSpeed = velocity.dot(track.up());
        double along = velocity.dot(track.tangent());

        boolean offRail = Math.abs(lateralError) > settings.maxLateralError()
                || Math.abs(verticalError) > settings.maxVerticalError();
        boolean overspeed = track.isOverspeed(Math.abs(along), settings.curveLateralLimit());
        boolean attaching = secondsSinceAssembly * 20.0 < settings.attachTicks()
                && Math.abs(lateralError) <= settings.attachRadius()
                && Math.abs(verticalError) <= settings.attachRadius();

        if (overspeed) {
            return new Output(Vec.ZERO, State.OVERSPEED, lateralError, verticalError,
                    targetSpeed - along);
        }
        if (offRail && !attaching) {
            return new Output(Vec.ZERO, State.OFF_RAIL, lateralError, verticalError,
                    targetSpeed - along);
        }

        // Every value below is an acceleration of the *whole* vehicle, so each axle contributes its
        // equal share and the shares add up to one vehicle. The flange spring and the weight are
        // therefore divided by the number of axles - driven or not - while traction is divided by
        // the number of driven axles, because only those pull.
        //
        // 1. Hold the axle on the rail, and carry the weight. The engine pulls the vehicle down with
        // the gravity of the dimension, so the rail gives exactly that back: one g in total, and the
        // vertical contact is stiff because steel on steel is stiff - a soft contact would let the
        // body float a hand's width above the rail head.
        double lateralAcceleration = -settings.guideStiffness() * lateralError
                - settings.guideDamping() * lateralSpeed;
        double verticalAcceleration = -settings.contactStiffness() * verticalError
                - settings.contactDamping() * verticalSpeed
                + gravity;

        double guide = Math.hypot(lateralAcceleration, verticalAcceleration);
        if (guide > settings.maxGuideAcceleration()) {
            double scale = settings.maxGuideAcceleration() / guide;
            lateralAcceleration *= scale;
            verticalAcceleration *= scale;
        }
        Vec support = track.lateral().scale(lateralAcceleration)
                .add(track.up().scale(verticalAcceleration));

        // 2. Traction along the rail, capped by what the wheel can actually transmit.
        double adhesion = settings.friction() * gravity;
        double demand;
        int alongShare;
        if (driven) {
            demand = clamp((targetSpeed - along) * 4.0, -settings.brake(), settings.traction());
            demand = clamp(demand, -Math.min(settings.brake(), adhesion),
                    Math.min(settings.traction(), adhesion));
            alongShare = Math.max(1, drivenCount);
        } else {
            // An idler does not pull: it only takes energy out of the vehicle.
            demand = clamp(-along * 0.8, -settings.brake(), 0.0);
            alongShare = Math.max(1, truckCount);
        }

        double supportShare = mass / Math.max(1, truckCount);
        Vec force = support.scale(supportShare)
                .add(track.tangent().scale(demand * mass / alongShare));

        // 3. Nothing turns the body from here. The force above is applied at the axle, so a bogie
        // that is pushed sideways at both ends produces the yaw itself.
        return new Output(force, State.GUIDING, lateralError, verticalError, targetSpeed - along);
    }

    /** Speed a driven truck asks for, in m/s, from the shaft and the way the truck faces. */
    public static double targetSpeed(float rpm, double facingSign, TruckSettings settings) {
        if (Math.abs(rpm) < 1.0e-3) {
            return 0.0;
        }
        return clamp(rpm * settings.speedPerRpm() * facingSign, -settings.maxSpeed(),
                settings.maxSpeed());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
