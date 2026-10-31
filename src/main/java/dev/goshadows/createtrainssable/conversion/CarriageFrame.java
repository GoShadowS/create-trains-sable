package dev.goshadows.createtrainssable.conversion;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.goshadows.createtrainssable.physics.Point3;
import dev.goshadows.createtrainssable.physics.Quat;
import net.minecraft.world.phys.Vec3;

/**
 * World-space frame of a carriage, read from the contraption entity Create ticks.
 *
 * <p>Create stores a carriage's position as an anchor and its rotation as yaw/pitch/roll on the
 * entity. Rotating the three basis vectors through {@link CarriageContraptionEntity#applyRotation}
 * turns that into a quaternion Sable can use, without depending on Flywheel matrices.</p>
 */
public record CarriageFrame(Point3 anchor, Quat orientation) {

    public static final CarriageFrame IDENTITY = new CarriageFrame(Point3.ZERO, Quat.IDENTITY);

    /**
     * @return the carriage frame, or {@code null} when Create has no present entity for it -
     *         the carriage is unloaded, or the train was disassembled.
     */
    public static CarriageFrame of(Carriage carriage) {
        CarriageContraptionEntity entity = carriage.anyAvailableEntity();
        if (entity == null) {
            return null;
        }
        Vec3 anchor = entity.getAnchorVec();
        Vec3 x = entity.applyRotation(new Vec3(1.0, 0.0, 0.0), 1.0f);
        Vec3 y = entity.applyRotation(new Vec3(0.0, 1.0, 0.0), 1.0f);
        Vec3 z = entity.applyRotation(new Vec3(0.0, 0.0, 1.0), 1.0f);
        Quat orientation = Quat.fromAxes(
                new Point3(x.x, x.y, x.z),
                new Point3(y.x, y.y, y.z),
                new Point3(z.x, z.y, z.z));
        return new CarriageFrame(new Point3(anchor.x, anchor.y, anchor.z), orientation);
    }
}
