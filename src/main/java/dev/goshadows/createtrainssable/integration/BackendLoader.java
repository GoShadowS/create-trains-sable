package dev.goshadows.createtrainssable.integration;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Loads the Sable backend only when the full mod is present. The backend lives in its own
 * class so that no Sable type is resolved while the game is running without it.
 */
public final class BackendLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SABLE_BACKEND =
            "dev.goshadows.createtrainssable.integration.sable.SablePhysicsBackend";

    private BackendLoader() {
    }

    public static PhysicsBackend load() {
        if (!ModList.get().isLoaded("sable")) {
            LOGGER.warn("Sable is not installed: Create Trains Sable runs in degraded mode, "
                    + "trains keep Create's original kinematics.");
            return new DegradedBackend("sable mod not loaded");
        }
        if (!companionIsOverridden()) {
            LOGGER.warn("Sable is present but its Companion service did not take priority; "
                    + "falling back to degraded mode.");
            return new DegradedBackend("Sable Companion still uses the default implementation");
        }
        try {
            Class<?> type = Class.forName(SABLE_BACKEND);
            return (PhysicsBackend) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            LOGGER.error("Could not initialise the Sable physics backend; degraded mode is active", e);
            return new DegradedBackend("Sable backend failed to load: " + e.getMessage());
        }
    }

    private static boolean companionIsOverridden() {
        try {
            return !SableCompanion.INSTANCE.getClass().getName()
                    .equals("dev.ryanhcode.sable.companion.impl.DefaultSableCompanion");
        } catch (LinkageError e) {
            LOGGER.error("Bundled Sable Companion is unusable", e);
            return false;
        }
    }
}
