package dev.goshadows.createtrainssable.config;

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
                new Tuning.Rebinding(RESYNC_MAX_SPEED.get(), RESYNC_MAX_ANGULAR_SPEED.get(),
                        RESYNC_MAX_TILT.get(), RESYNC_MAX_TRACK_DISTANCE.get(), RESYNC_STABLE_TICKS.get()),
                new Tuning.Debug(SHOW_RAPIER_HITBOXES.get())).validated();
    }
}
