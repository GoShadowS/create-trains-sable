package dev.goshadows.createtrainssable.config;

import dev.goshadows.createtrainssable.rail.TruckSettings;
import dev.goshadows.createtrainssable.physics.Tuning;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side TOML configuration. Nothing is cached: config reloads take effect next tick. */
public final class CTSConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.EnumValue<Tuning.ConversionMode> MODE;
    private static final ModConfigSpec.IntValue MAX_ACTIVE_TRAINS;
    private static final ModConfigSpec.IntValue SESSION_TIMEOUT;

    private static final ModConfigSpec.DoubleValue BLOCK_MASS_SCALE;
    private static final ModConfigSpec.DoubleValue MINIMUM_BLOCK_MASS;
    private static final ModConfigSpec.DoubleValue WHEEL_RAIL_FRICTION;
    private static final ModConfigSpec.DoubleValue ROLLING_RESISTANCE;
    private static final ModConfigSpec.DoubleValue BRAKE_FORCE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue SLIP_THRESHOLD;
    private static final ModConfigSpec.DoubleValue GRAVITY;

    private static final ModConfigSpec.DoubleValue CANT_RELIEF_FACTOR;
    private static final ModConfigSpec.DoubleValue FLANGE_COEFFICIENT;
    private static final ModConfigSpec.DoubleValue HALF_GAUGE;
    private static final ModConfigSpec.DoubleValue CENTRE_OF_GRAVITY_HEIGHT;
    private static final ModConfigSpec.DoubleValue MAX_CURVE_RADIUS;
    private static final ModConfigSpec.DoubleValue OVERSPEED_GRACE;
    private static final ModConfigSpec.DoubleValue IMPACT_SPEED_THRESHOLD;
    private static final ModConfigSpec.DoubleValue HARD_BRAKE_DECELERATION;

    private static final ModConfigSpec.DoubleValue RESYNC_MAX_SPEED;
    private static final ModConfigSpec.DoubleValue RESYNC_MAX_ANGULAR_SPEED;
    private static final ModConfigSpec.DoubleValue RESYNC_MAX_TILT;
    private static final ModConfigSpec.DoubleValue RESYNC_MAX_TRACK_DISTANCE;
    private static final ModConfigSpec.IntValue RESYNC_STABLE_TICKS;
    private static final ModConfigSpec.BooleanValue AUTO_RESYNC;

    private static final ModConfigSpec.BooleanValue PHYSICAL_RAILS;
    private static final ModConfigSpec.DoubleValue RAIL_POSITION_GAIN;
    private static final ModConfigSpec.DoubleValue RAIL_VELOCITY_GAIN;
    private static final ModConfigSpec.DoubleValue RAIL_MAX_ACCELERATION;
    private static final ModConfigSpec.DoubleValue RAIL_ANGULAR_GAIN;
    private static final ModConfigSpec.DoubleValue RAIL_ANGULAR_VELOCITY_GAIN;
    private static final ModConfigSpec.DoubleValue RAIL_MAX_ANGULAR_ACCELERATION;
    private static final ModConfigSpec.DoubleValue RAIL_ANGLE_TOLERANCE;
    private static final ModConfigSpec.BooleanValue RAILS_COUPLING_ENABLED;
    private static final ModConfigSpec.DoubleValue RAILS_COUPLING_STIFFNESS;
    private static final ModConfigSpec.DoubleValue RAILS_COUPLING_DAMPING;
    private static final ModConfigSpec.DoubleValue RAILS_COUPLING_MAX_ACCELERATION;
    private static final ModConfigSpec.DoubleValue RAIL_LATERAL_TOLERANCE;
    private static final ModConfigSpec.IntValue RAIL_TOLERANCE_TICKS;

    private static final ModConfigSpec.BooleanValue CONVERT_TRAINS;
    private static final ModConfigSpec.DoubleValue TRUCK_SPEED_PER_RPM;
    private static final ModConfigSpec.DoubleValue TRUCK_TRACTION;
    private static final ModConfigSpec.DoubleValue TRUCK_BRAKE;
    private static final ModConfigSpec.DoubleValue TRUCK_GUIDE_STIFFNESS;
    private static final ModConfigSpec.DoubleValue TRUCK_GUIDE_DAMPING;
    private static final ModConfigSpec.DoubleValue TRUCK_MAX_GUIDE;
    private static final ModConfigSpec.DoubleValue TRUCK_ALIGN_GAIN;
    private static final ModConfigSpec.DoubleValue TRUCK_ALIGN_DAMPING;
    private static final ModConfigSpec.DoubleValue TRUCK_MAX_ANGULAR;
    private static final ModConfigSpec.DoubleValue TRUCK_MAX_LATERAL;
    private static final ModConfigSpec.DoubleValue TRUCK_MAX_VERTICAL;
    private static final ModConfigSpec.DoubleValue TRUCK_CURVE_LIMIT;
    private static final ModConfigSpec.DoubleValue TRUCK_SCAN_RADIUS;
    private static final ModConfigSpec.IntValue TRUCK_RECHECK_TICKS;
    private static final ModConfigSpec.DoubleValue COUPLING_RANGE;
    private static final ModConfigSpec.DoubleValue COUPLING_STIFFNESS;
    private static final ModConfigSpec.DoubleValue COUPLING_DAMPING;
    private static final ModConfigSpec.DoubleValue COUPLING_MAX_ACCELERATION;

    private static final ModConfigSpec.BooleanValue CONVERT_ON_ASSEMBLY;
    private static final ModConfigSpec.BooleanValue SHOW_RAPIER_HITBOXES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Global switches for the physics hand-off.").push("general");
        ENABLED = builder.comment("Set to false to leave Create trains entirely alone.")
                .define("enabled", true);
        MODE = builder.comment("THRESHOLD, ALWAYS or DISABLED. ALWAYS is meant for tuning.")
                .defineEnum("conversionMode", Tuning.ConversionMode.THRESHOLD);
        MAX_ACTIVE_TRAINS = builder.comment("Maximum number of trains simulated at once.")
                .defineInRange("maxActiveTrains", 10, 1, 64);
        SESSION_TIMEOUT = builder.comment("Ticks before an unrecoverable wreck is abandoned.")
                .defineInRange("sessionTimeoutTicks", 12000, 200, 200000);
        CONVERT_TRAINS = builder.comment(
                        "Replace Create trains with Sable structures (the old behaviour).",
                        "Off by default: vehicles are now built out of rail trucks instead, which needs no",
                        "conversion and cannot produce a second copy of the train.")
                .define("convertCreateTrains", false);
        CONVERT_ON_ASSEMBLY = builder.comment(
                        "Represent every train as Sable sub-levels as soon as it exists, instead of waiting for a derailment.",
                        "Disable this to keep Create's own contraption rendering until a train crashes.")
                .define("convertOnAssembly", true);
        builder.pop();

        builder.comment("Wheel/rail contact. Mass is in Sable's kpg unit, forces in kpg*m/s^2.")
                .push("traction");
        BLOCK_MASS_SCALE = builder.comment("Mass estimate per point of block destroy speed.")
                .defineInRange("blockMassScale", 0.5, 0.01, 20.0);
        MINIMUM_BLOCK_MASS = builder.comment("Floor for the mass estimate of a single block.")
                .defineInRange("minimumBlockMass", 0.1, 0.001, 10.0);
        WHEEL_RAIL_FRICTION = builder.comment("Adhesion coefficient between steel wheels and rails.")
                .defineInRange("wheelRailFriction", 0.45, 0.01, 2.0);
        ROLLING_RESISTANCE = builder.comment("Constant rolling resistance coefficient.")
                .defineInRange("rollingResistance", 0.02, 0.0, 1.0);
        BRAKE_FORCE_MULTIPLIER = builder.comment("Brake demand multiplier; values above 1 cause wheel slip.")
                .defineInRange("brakeForceMultiplier", 1.0, 0.0, 5.0);
        SLIP_THRESHOLD = builder.comment("Slip ratio above which a wheel is considered to be spinning.")
                .defineInRange("slipThreshold", 0.18, 0.01, 1.0);
        GRAVITY = builder.comment("Gravity in m/s^2. Sable's own dimension settings are not overridden.")
                .defineInRange("gravity", 9.81, 0.1, 50.0);
        builder.pop();

        builder.comment("Derailment thresholds. Create speed is in blocks/tick and is converted at 20 tps.")
                .push("derailment");
        CANT_RELIEF_FACTOR = builder.comment("Extra lateral capacity from the banking of Create turns.")
                .defineInRange("cantReliefFactor", 1.8, 0.1, 10.0);
        FLANGE_COEFFICIENT = builder.comment("Wheel-climb (Nadal-style) coefficient.")
                .defineInRange("flangeCoefficient", 0.45, 0.01, 2.0);
        HALF_GAUGE = builder.comment("Half the track gauge in blocks, used for the overturning limit.")
                .defineInRange("halfGauge", 0.75, 0.1, 5.0);
        CENTRE_OF_GRAVITY_HEIGHT = builder.comment("Carriage centre of gravity above rail head, in blocks.")
                .defineInRange("centreOfGravityHeight", 1.1, 0.1, 10.0);
        MAX_CURVE_RADIUS = builder.comment("Curves wider than this are treated as straight track.")
                .defineInRange("maxCurveRadius", 64.0, 1.0, 1024.0);
        OVERSPEED_GRACE = builder.comment("Hysteresis above the safe speed before a conversion starts.")
                .defineInRange("overspeedGrace", 1.15, 1.0, 5.0);
        IMPACT_SPEED_THRESHOLD = builder.comment("Closing speed in m/s that counts as a collision.")
                .defineInRange("impactSpeedThreshold", 4.0, 0.0, 100.0);
        HARD_BRAKE_DECELERATION = builder.comment("Emergency deceleration in m/s^2 that trips a conversion.")
                .defineInRange("hardBrakeDeceleration", 18.0, 0.0, 200.0);
        builder.pop();

        builder.comment("Conditions for returning a settled body to its TrackGraph.").push("rebinding");
        RESYNC_MAX_SPEED = builder.comment("Maximum linear speed in m/s.")
                .defineInRange("maxSpeed", 0.25, 0.0, 10.0);
        RESYNC_MAX_ANGULAR_SPEED = builder.comment("Maximum angular speed in rad/s.")
                .defineInRange("maxAngularSpeed", 0.10, 0.0, 10.0);
        RESYNC_MAX_TILT = builder.comment("Maximum tilt from vertical, in degrees.")
                .defineInRange("maxTiltDegrees", 12.0, 0.0, 180.0);
        RESYNC_MAX_TRACK_DISTANCE = builder.comment("Maximum horizontal distance to a track node, in blocks.")
                .defineInRange("maxTrackDistance", 1.5, 0.0, 32.0);
        RESYNC_STABLE_TICKS = builder.comment("Consecutive stable ticks required before rebinding.")
                .defineInRange("stableTicks", 40, 1, 1000);
        AUTO_RESYNC = builder.comment(
                        "Put a settled wreck back on the nearest track automatically.",
                        "Off by default: the wreck stays a physical Sable structure where it fell, like any other Sable body.")
                .define("autoResync", false);
        builder.pop();

        builder.comment(
                        "How a running train is held on its rails.",
                        "Physical rails push the carriages with forces and never move them, so a train can be",
                        "pushed off the track. Kinematic rails teleport the carriage structure onto Create's",
                        "carriages every tick: cheaper, but nothing can ever derail a moving train.")
                .push("rails");
        PHYSICAL_RAILS = builder.comment("Use forces instead of teleporting the structure.")
                .define("physical", true);
        RAIL_POSITION_GAIN = builder.comment("Position stiffness in 1/s^2.")
                .defineInRange("positionGain", 10.0, 0.0, 100.0);
        RAIL_VELOCITY_GAIN = builder.comment("Velocity damping in 1/s.")
                .defineInRange("velocityGain", 4.0, 0.0, 100.0);
        RAIL_MAX_ACCELERATION = builder.comment(
                        "Largest acceleration the wheel/rail contact can deliver, in m/s^2.",
                        "Exceeding it is wheel slip or, if it lasts, a derailment.")
                .defineInRange("maxAcceleration", 24.0, 0.1, 500.0);
        RAIL_ANGULAR_GAIN = builder.comment("Orientation stiffness in 1/s^2.")
                .defineInRange("angularGain", 5.0, 0.0, 100.0);
        RAIL_ANGULAR_VELOCITY_GAIN = builder.comment("Orientation damping in 1/s.")
                .defineInRange("angularVelocityGain", 2.0, 0.0, 100.0);
        RAIL_MAX_ANGULAR_ACCELERATION = builder.comment("Largest angular acceleration the rails deliver.")
                .defineInRange("maxAngularAcceleration", 12.0, 0.1, 500.0);
        RAIL_ANGLE_TOLERANCE = builder.comment("Orientation error in radians above which the rails count as overloaded.")
                .defineInRange("angleTolerance", 0.35, 0.0, 3.1415);
        RAILS_COUPLING_ENABLED = builder.comment(
                        "Couple the carriages of a converted train with a spring and damper.")
                .define("coupling", true);
        RAILS_COUPLING_STIFFNESS = builder.comment("Coupling stiffness in 1/s^2 per block of extension.")
                .defineInRange("couplingStiffness", 8.0, 0.0, 10000.0);
        RAILS_COUPLING_DAMPING = builder.comment("Coupling damping in 1/s.")
                .defineInRange("couplingDamping", 4.0, 0.0, 10000.0);
        RAILS_COUPLING_MAX_ACCELERATION = builder.comment(
                        "Strongest acceleration a coupling may transfer, in m/s^2.")
                .defineInRange("couplingMaxAcceleration", 20.0, 0.0, 500.0);
        RAIL_LATERAL_TOLERANCE = builder.comment("Distance from the ideal carriage pose that counts as leaving the rails, in blocks.")
                .defineInRange("lateralTolerance", 1.5, 0.0, 16.0);
        RAIL_TOLERANCE_TICKS = builder.comment("Consecutive overloaded ticks before the train derails.")
                .defineInRange("toleranceTicks", 10, 1, 200);
        builder.pop();

        builder.comment(
                        "Rail trucks: the blocks that put a Sable vehicle on Create rails.",
                        "Build the vehicle, put trucks under it, assemble it, drive it with a shaft.")
                .push("trucks");
        TRUCK_SPEED_PER_RPM = builder.comment(
                        "Metres per second of target speed per RPM of the driving shaft.",
                        "32 RPM therefore means about 1.6 blocks per second with the default value.")
                .defineInRange("speedPerRpm", 0.05, 0.0, 5.0);
        TRUCK_TRACTION = builder.comment("Acceleration the driven wheels can put down, in m/s^2.")
                .defineInRange("tractionAcceleration", 1.2, 0.0, 100.0);
        TRUCK_BRAKE = builder.comment("Acceleration the brakes can remove, in m/s^2.")
                .defineInRange("brakeAcceleration", 1.6, 0.0, 100.0);
        TRUCK_GUIDE_STIFFNESS = builder.comment("How firmly the axle is held on the rail, in 1/s^2.")
                .defineInRange("guideStiffness", 18.0, 0.0, 500.0);
        TRUCK_GUIDE_DAMPING = builder.comment("Damping of the axle on the rail, in 1/s.")
                .defineInRange("guideDamping", 6.0, 0.0, 200.0);
        TRUCK_MAX_GUIDE = builder.comment("Largest acceleration the rail contact may deliver, in m/s^2.")
                .defineInRange("maxGuideAcceleration", 26.0, 0.1, 500.0);
        TRUCK_ALIGN_GAIN = builder.comment("How strongly the vehicle is turned to follow the rail.")
                .defineInRange("alignGain", 3.0, 0.0, 100.0);
        TRUCK_ALIGN_DAMPING = builder.comment("Damping of that rotation, in 1/s.")
                .defineInRange("alignDamping", 1.5, 0.0, 100.0);
        TRUCK_MAX_ANGULAR = builder.comment("Largest angular acceleration the rails deliver, in rad/s^2.")
                .defineInRange("maxAngularAcceleration", 8.0, 0.1, 100.0);
        TRUCK_MAX_LATERAL = builder.comment(
                        "Lateral distance from the rail that still counts as on the rail, in blocks.",
                        "Beyond it the truck is off the track and the vehicle is a wreck.")
                .defineInRange("maxLateralError", 1.2, 0.05, 8.0);
        TRUCK_MAX_VERTICAL = builder.comment(
                        "Vertical distance from the rail head that still counts as on the rail.")
                .defineInRange("maxVerticalError", 0.9, 0.05, 8.0);
        TRUCK_CURVE_LIMIT = builder.comment(
                        "Lateral acceleration the flanges can take before the vehicle leaves the rail, in m/s^2.",
                        "This is what makes a curve taken too fast derail instead of holding.")
                .defineInRange("curveLateralLimit", 2.6, 0.1, 100.0);
        TRUCK_SCAN_RADIUS = builder.comment("How far to look for track around a truck, in blocks.")
                .defineInRange("scanRadius", 24.0, 2.0, 128.0);
        TRUCK_RECHECK_TICKS = builder.comment("Ticks between track lookups; physics runs faster than this.")
                .defineInRange("trackRecheckTicks", 2, 1, 40);
        COUPLING_RANGE = builder.comment(
                        "Distance within which two spring couplings facing each other link, in blocks.")
                .defineInRange("couplingRange", 2.5, 0.5, 16.0);
        COUPLING_STIFFNESS = builder.comment("Coupling stiffness in 1/s^2 per block of extension.")
                .defineInRange("couplingStiffness", 6.0, 0.0, 200.0);
        COUPLING_DAMPING = builder.comment("Coupling damping in 1/s.")
                .defineInRange("couplingDamping", 4.0, 0.0, 200.0);
        COUPLING_MAX_ACCELERATION = builder.comment(
                        "Strongest acceleration a coupler may transfer, in m/s^2.")
                .defineInRange("couplingMaxAcceleration", 14.0, 0.0, 200.0);
        builder.pop();

        builder.comment("Diagnostics.").push("debug");
        SHOW_RAPIER_HITBOXES = builder.comment(
                        "Log body poses, velocities and bounds; Sable draws the actual colliders in its client overlay.")
                .define("showRapierHitboxes", false);
        builder.pop();

        SPEC = builder.build();
    }

    private CTSConfig() {
    }

    /** True when the old train conversion is enabled; off by default. */
    public static boolean convertTrains() {
        return CONVERT_TRAINS.get();
    }

    /** Truck and coupling tuning, read on every physics tick. */
    public static TruckSettings trucks() {
        return new TruckSettings(
                TRUCK_SPEED_PER_RPM.get(), TRUCK_TRACTION.get(), TRUCK_BRAKE.get(),
                TRUCK_GUIDE_STIFFNESS.get(), TRUCK_GUIDE_DAMPING.get(), TRUCK_MAX_GUIDE.get(),
                TRUCK_ALIGN_GAIN.get(), TRUCK_ALIGN_DAMPING.get(), TRUCK_MAX_ANGULAR.get(),
                TRUCK_MAX_LATERAL.get(), TRUCK_MAX_VERTICAL.get(), TRUCK_CURVE_LIMIT.get(),
                TRUCK_SCAN_RADIUS.get(), TRUCK_RECHECK_TICKS.get(), COUPLING_RANGE.get(),
                COUPLING_STIFFNESS.get(), COUPLING_DAMPING.get(), COUPLING_MAX_ACCELERATION.get(),
                GRAVITY.get()).validated();
    }

    /** True when assembled trains should be replaced by Sable structures straight away. */
    public static boolean convertOnAssembly() {
        return CONVERT_ON_ASSEMBLY.get();
    }

    public static Tuning snapshot() {
        return new Tuning(
                new Tuning.General(ENABLED.get(), MODE.get(), MAX_ACTIVE_TRAINS.get(), SESSION_TIMEOUT.get()),
                new Tuning.Traction(BLOCK_MASS_SCALE.get(), MINIMUM_BLOCK_MASS.get(),
                        WHEEL_RAIL_FRICTION.get(), ROLLING_RESISTANCE.get(),
                        BRAKE_FORCE_MULTIPLIER.get(), SLIP_THRESHOLD.get(), GRAVITY.get()),
                new Tuning.Derailment(CANT_RELIEF_FACTOR.get(), FLANGE_COEFFICIENT.get(),
                        HALF_GAUGE.get(), CENTRE_OF_GRAVITY_HEIGHT.get(), MAX_CURVE_RADIUS.get(),
                        OVERSPEED_GRACE.get(), IMPACT_SPEED_THRESHOLD.get(), HARD_BRAKE_DECELERATION.get()),
                new Tuning.Rails(PHYSICAL_RAILS.get(), RAIL_POSITION_GAIN.get(), RAIL_VELOCITY_GAIN.get(),
                        RAIL_MAX_ACCELERATION.get(), RAIL_ANGULAR_GAIN.get(),
                        RAIL_ANGULAR_VELOCITY_GAIN.get(), RAIL_MAX_ANGULAR_ACCELERATION.get(),
                        RAIL_ANGLE_TOLERANCE.get(), RAILS_COUPLING_ENABLED.get(),
                        RAILS_COUPLING_STIFFNESS.get(), RAILS_COUPLING_DAMPING.get(),
                        RAILS_COUPLING_MAX_ACCELERATION.get(),
                        RAIL_LATERAL_TOLERANCE.get(), RAIL_TOLERANCE_TICKS.get()),
                new Tuning.Rebinding(RESYNC_MAX_SPEED.get(), RESYNC_MAX_ANGULAR_SPEED.get(),
                        RESYNC_MAX_TILT.get(), RESYNC_MAX_TRACK_DISTANCE.get(), RESYNC_STABLE_TICKS.get(),
                        AUTO_RESYNC.get()),
                new Tuning.Debug(SHOW_RAPIER_HITBOXES.get())).validated();
    }
}
