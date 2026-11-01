package dev.goshadows.createtrainssable.bridge;

import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CreateTrainsSable;

import java.lang.reflect.Field;

/**
 * The bridge's link to Create's deformation array, {@code Train.stress}.
 *
 * <p>Create fills that array from the distance between a carriage's contraption anchors. A bridged
 * carriage has no contraption, so Create finds no anchors, keeps its fallback and publishes a zero
 * for every gap - which makes a bridged train's deformation invisible in Create's own state while
 * the springs are the only thing actually holding the consist together. The bridge therefore
 * <em>writes</em> the physical deformation into the array rather than reading it: reading would only
 * copy Create's zero back.</p>
 *
 * <p>The field is package-private, so this reaches it reflectively. If a future Create release
 * renames or hides it, {@link #available()} turns false and every write becomes a no-op - the harness
 * fails loudly on that instead of the mod quietly publishing a number nobody can see.</p>
 */
public final class TrainStress {

    /** Create's own limit: {@code Train.tick} derails a train whose largest stress passes this. */
    public static final double LIMIT = 4.0;

    private static final Field FIELD = find();
    private static boolean warned;

    private TrainStress() {
    }

    private static Field find() {
        try {
            Field field = Train.class.getDeclaredField("stress");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Whether Create's deformation array can be reached at all. */
    public static boolean available() {
        return FIELD != null;
    }

    /** A copy of what Create currently publishes, or {@code null} when the field is out of reach. */
    public static double[] read(Train train) {
        if (FIELD == null || train == null) {
            return null;
        }
        try {
            Object value = FIELD.get(train);
            return value instanceof double[] values ? values.clone() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    /**
     * Publishes one deformation per gap, in Create's sign convention: positive means the two
     * carriages should move apart.
     *
     * @return whether the array was written
     */
    public static boolean write(Train train, double[] deformation) {
        if (FIELD == null || train == null || deformation == null) {
            return false;
        }
        try {
            Object current = FIELD.get(train);
            if (current instanceof double[] existing && existing.length == deformation.length) {
                // Write through the array Create already holds: anything that kept a reference sees
                // the new value without knowing where it came from.
                System.arraycopy(deformation, 0, existing, 0, deformation.length);
            } else {
                FIELD.set(train, deformation.clone());
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    private static void warn(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        CreateTrainsSable.LOGGER.warn("Create's Train.stress is out of reach, so a bridged train "
                + "cannot publish its deformation: {}", e.toString());
    }
}
