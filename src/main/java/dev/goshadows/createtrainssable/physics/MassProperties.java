package dev.goshadows.createtrainssable.physics;

import java.util.List;

/**
 * Mass, centre of mass and inertia tensor of a carriage.
 *
 * <p>The tensor is row-major and expressed about the centre of mass. Diagonal terms use the
 * point-mass sum plus the intrinsic inertia of each cubic block; off-diagonal terms are the
 * products of inertia with the conventional negative sign, so
 * {@code angularMomentum = I * omega} holds directly.</p>
 */
public final class MassProperties {

    /** Sable's default per-block inertia multiplier for a solid cube. */
    public static final double CUBE_INERTIA_FACTOR = 1.0 / 6.0;

    public static final MassProperties EMPTY =
            new MassProperties(0.0, Point3.ZERO, new double[9], 0.0);

    private final double mass;
    private final Point3 centerOfMass;
    private final double[] inertia;
    private final double maxExtent;

    private MassProperties(double mass, Point3 centerOfMass, double[] inertia, double maxExtent) {
        this.mass = mass;
        this.centerOfMass = centerOfMass;
        this.inertia = inertia;
        this.maxExtent = maxExtent;
    }

    public static MassProperties fromBlocks(List<WeightedBlock> blocks, double inertiaFactor) {
        if (blocks.isEmpty()) {
            return EMPTY;
        }

        double totalMass = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        for (WeightedBlock block : blocks) {
            double blockMass = Math.max(0.0, block.mass());
            totalMass += blockMass;
            sumX += block.position().x() * blockMass;
            sumY += block.position().y() * blockMass;
            sumZ += block.position().z() * blockMass;
        }
        if (totalMass <= 0.0) {
            return EMPTY;
        }

        Point3 center = new Point3(sumX / totalMass, sumY / totalMass, sumZ / totalMass);
        double[] tensor = new double[9];
        double extent = 0.0;
        for (WeightedBlock block : blocks) {
            double blockMass = Math.max(0.0, block.mass());
            Point3 offset = block.position().sub(center);
            double dx = offset.x();
            double dy = offset.y();
            double dz = offset.z();
            double intrinsic = blockMass * inertiaFactor;

            tensor[0] += blockMass * (dy * dy + dz * dz) + intrinsic;
            tensor[4] += blockMass * (dx * dx + dz * dz) + intrinsic;
            tensor[8] += blockMass * (dx * dx + dy * dy) + intrinsic;
            tensor[1] -= blockMass * dx * dy;
            tensor[2] -= blockMass * dx * dz;
            tensor[5] -= blockMass * dy * dz;
            extent = Math.max(extent, offset.length());
        }
        tensor[3] = tensor[1];
        tensor[6] = tensor[2];
        tensor[7] = tensor[5];

        return new MassProperties(totalMass, center, tensor, extent);
    }

    public double mass() {
        return mass;
    }

    public Point3 centerOfMass() {
        return centerOfMass;
    }

    /** @return a copy of the 3x3 inertia tensor in row-major order. */
    public double[] inertia() {
        return inertia.clone();
    }

    public double maxExtent() {
        return maxExtent;
    }

    public boolean isEmpty() {
        return mass <= 0.0;
    }

    /** Effective inertia about a unit axis through the centre of mass. */
    public double inertiaAlong(Point3 axis) {
        Point3 unit = axis.normalized();
        double[] m = inertia;
        double ix = m[0] * unit.x() + m[1] * unit.y() + m[2] * unit.z();
        double iy = m[3] * unit.x() + m[4] * unit.y() + m[5] * unit.z();
        double iz = m[6] * unit.x() + m[7] * unit.y() + m[8] * unit.z();
        return unit.x() * ix + unit.y() * iy + unit.z() * iz;
    }

    public double kineticEnergy(double speedMetresPerSecond, Point3 angularVelocity) {
        double rotational = 0.0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                rotational += inertia[row * 3 + col] * component(angularVelocity, row)
                        * component(angularVelocity, col);
            }
        }
        return 0.5 * mass * speedMetresPerSecond * speedMetresPerSecond + 0.5 * rotational;
    }

    private static double component(Point3 vector, int index) {
        return switch (index) {
            case 0 -> vector.x();
            case 1 -> vector.y();
            default -> vector.z();
        };
    }
}
