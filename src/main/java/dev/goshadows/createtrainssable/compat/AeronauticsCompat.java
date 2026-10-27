package dev.goshadows.createtrainssable.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Position handling for worlds that also run the Aeronautics modules. Their sub-levels live
 * at extreme plot coordinates, so distances must be projected through Companion before any
 * track lookup. No Aeronautics class is referenced here.
 */
public final class AeronauticsCompat {

    private static final List<String> MOD_IDS = List.of("simulated", "aeronautics", "offroad");

    private AeronauticsCompat() {
    }

    public static boolean isPresent() {
        for (String modId : MOD_IDS) {
            if (ModList.get().isLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    public static Vec3 projectToWorld(Level level, Vec3 position) {
        return isPresent() ? SableCompanion.INSTANCE.projectOutOfSubLevel(level, position) : position;
    }

    /** Rebinding inside a moving airship would pin the train to a plot, not to a track. */
    public static boolean isInsideSubLevel(Level level, Vec3 position) {
        return isPresent() && SableCompanion.INSTANCE.getContaining(level, position) != null;
    }
}
