package dev.goshadows.createtrainssable.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration: one section for the trucks, one for the couplings, one for diagnostics. */
public final class CTSConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue SPEED_PER_RPM;
    private static final ModConfigSpec.DoubleValue MAX_SPEED;
    private static final ModConfigSpec.DoubleValue TRACTION;
    private static final ModConfigSpec.DoubleValue BRAKE;
    private static final ModConfigSpec.DoubleValue FRICTION;
    private static final ModConfigSpec.DoubleValue GUIDE_STIFFNESS;
    private static final ModConfigSpec.DoubleValue GUIDE_DAMPING;
    private static final ModConfigSpec.DoubleValue CONTACT_STIFFNESS;
    private static final ModConfigSpec.DoubleValue CONTACT_DAMPING;
    private static final ModConfigSpec.DoubleValue MAX_GUIDE;
    private static final ModConfigSpec.DoubleValue MAX_LATERAL;
    private static final ModConfigSpec.DoubleValue MAX_VERTICAL;
    private static final ModConfigSpec.DoubleValue CURVE_LIMIT;
    private static final ModConfigSpec.DoubleValue SCAN_RADIUS;
    private static final ModConfigSpec.IntValue RECHECK_TICKS;
    private static final ModConfigSpec.IntValue ATTACH_TICKS;
    private static final ModConfigSpec.DoubleValue ATTACH_RADIUS;
    private static final ModConfigSpec.DoubleValue COUPLING_RANGE;
    private static final ModConfigSpec.DoubleValue COUPLING_STIFFNESS;
    private static final ModConfigSpec.DoubleValue COUPLING_DAMPING;
    private static final ModConfigSpec.DoubleValue COUPLING_MAX;
    private static final ModConfigSpec.DoubleValue GRAVITY;
    private static final ModConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                        "Rail trucks: the blocks that put a Sable vehicle on Create rails.",
                        "Put trucks under the frame, assemble the vehicle, and each axle holds itself on",
                        "the rail with force. Nothing is teleported: a wall or a curve can win.")
                .push("trucks");
        SPEED_PER_RPM = builder.comment(
                        "Target speed in m/s per RPM of the driving shaft.",
                        "With the default, 32 RPM is about 1.6 blocks per second.")
                .defineInRange("speedPerRpm", 0.05, 0.0, 5.0);
        MAX_SPEED = builder.comment("Highest speed a truck will ever command, in m/s.")
                .defineInRange("maxSpeed", 60.0, 0.1, 200.0);
        TRACTION = builder.comment("Acceleration the driven wheels can put down, in m/s^2.")
                .defineInRange("traction", 1.2, 0.0, 100.0);
        BRAKE = builder.comment("Acceleration the brakes can remove, in m/s^2.")
                .defineInRange("brake", 1.6, 0.0, 100.0);
        FRICTION = builder.comment(
                        "Adhesion coefficient of steel on steel; caps the force a wheel can transmit.",
                        "Above one the wheels would grip better than the weight they carry.")
                .defineInRange("friction", 0.45, 0.0, 2.0);
        GUIDE_STIFFNESS = builder.comment("How hard the flange pushes the axle back onto the rail, in 1/s^2.")
                .defineInRange("guideStiffness", 18.0, 0.0, 500.0);
        GUIDE_DAMPING = builder.comment("Damping of that push, in 1/s.")
                .defineInRange("guideDamping", 6.0, 0.0, 200.0);
        CONTACT_STIFFNESS = builder.comment(
                        "Stiffness of the vertical contact, in 1/s^2 per block.",
                        "Steel on steel is stiff: this is what keeps the wheels on the rail head",
                        "instead of floating above it by the sag of the flange spring.")
                .defineInRange("contactStiffness", 220.0, 1.0, 2000.0);
        CONTACT_DAMPING = builder.comment("Damping of the vertical contact, in 1/s.")
                .defineInRange("contactDamping", 24.0, 0.0, 500.0);
        MAX_GUIDE = builder.comment("Largest acceleration the wheel/rail contact may deliver, in m/s^2.")
                .defineInRange("maxGuideAcceleration", 26.0, 0.1, 500.0);
        MAX_LATERAL = builder.comment(
                        "Lateral distance from the rail head that still counts as on the rail, in blocks.",
                        "Beyond it the flange has climbed the rail: the truck stops guiding.")
                .defineInRange("maxLateralError", 1.2, 0.05, 8.0);
        MAX_VERTICAL = builder.comment(
                        "Vertical distance from the rail head that still counts as on the rail, in blocks.")
                .defineInRange("maxVerticalError", 0.9, 0.05, 8.0);
        CURVE_LIMIT = builder.comment(
                        "Lateral acceleration the flanges can take before the vehicle leaves the rail, in m/s^2.",
                        "This is what makes a curve taken too fast a derailment instead of a hold.")
                .defineInRange("curveLateralLimit", 2.6, 0.1, 100.0);
        SCAN_RADIUS = builder.comment("How far to look for track around an axle, in blocks.")
                .defineInRange("scanRadius", 24.0, 2.0, 128.0);
        RECHECK_TICKS = builder.comment("Ticks between track searches; the contact itself runs every substep.")
                .defineInRange("trackRecheckTicks", 2, 1, 40);
        ATTACH_TICKS = builder.comment(
                        "Ticks after assembly in which an axle may be pulled onto a rail it is not on yet.")
                .defineInRange("attachTicks", 40, 0, 400);
        ATTACH_RADIUS = builder.comment(
                        "How far from the rail head an axle may start and still be attached, in blocks.")
                .defineInRange("attachRadius", 3.0, 0.0, 16.0);
        builder.pop();

        builder.comment("Spring couplings between vehicles.").push("couplings");
        COUPLING_RANGE = builder.comment(
                        "Distance within which two couplings facing each other link, in blocks.")
                .defineInRange("range", 2.5, 0.5, 16.0);
        COUPLING_STIFFNESS = builder.comment("Stiffness of the spring, in 1/s^2 per block of extension.")
                .defineInRange("stiffness", 6.0, 0.0, 200.0);
        COUPLING_DAMPING = builder.comment("Damping of the spring, in 1/s.")
                .defineInRange("damping", 4.0, 0.0, 200.0);
        COUPLING_MAX = builder.comment("Strongest acceleration a coupler may transfer, in m/s^2.")
                .defineInRange("maxAcceleration", 14.0, 0.0, 200.0);
        builder.pop();

        builder.comment("Diagnostics.").push("debug");
        GRAVITY = builder.comment("Fallback gravity for the wheel/rail contact, in m/s^2. The",
                        "contact normally uses the gravity Sable applies to the dimension.")
                .defineInRange("gravity", 9.81, 0.1, 50.0);
        DEBUG_LOG = builder.comment("Log the state of every truck once a second.")
                .define("logTrucks", false);
        builder.pop();

        SPEC = builder.build();
    }

    private CTSConfig() {
    }

    /** Truck and coupling tuning, read on every physics substep. */
    public static TruckSettings trucks() {
        return new TruckSettings(
                SPEED_PER_RPM.get(), MAX_SPEED.get(),
                TRACTION.get(), BRAKE.get(), FRICTION.get(),
                GUIDE_STIFFNESS.get(), GUIDE_DAMPING.get(),
                CONTACT_STIFFNESS.get(), CONTACT_DAMPING.get(), MAX_GUIDE.get(),
                MAX_LATERAL.get(), MAX_VERTICAL.get(), CURVE_LIMIT.get(),
                SCAN_RADIUS.get(), RECHECK_TICKS.get(), ATTACH_TICKS.get(), ATTACH_RADIUS.get(),
                COUPLING_RANGE.get(), COUPLING_STIFFNESS.get(), COUPLING_DAMPING.get(),
                COUPLING_MAX.get(),
                GRAVITY.get(), DEBUG_LOG.get()).validated();
    }
}
