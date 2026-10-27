package dev.goshadows.createtrainssable.debug;

import com.mojang.logging.LogUtils;
import dev.goshadows.createtrainssable.integration.AttachedBody;
import dev.goshadows.createtrainssable.physics.Pose;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Console-side view of the Rapier bodies. Sable renders the real colliders in its own client
 * debug overlay; this trace exists so a server log shows the same state without a client.
 */
public final class RapierDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RapierDebug() {
    }

    public static void logBodies(UUID trainId, List<AttachedBody> bodies, int ageTicks) {
        for (int i = 0; i < bodies.size(); i++) {
            Pose pose = bodies.get(i).samplePose();
            LOGGER.info("[CTS] train={} body={}/{} t={} pos=({},{},{}), v={} m/s, w={} rad/s, tilt={} deg",
                    trainId, i, bodies.size(), ageTicks,
                    pose.position().x(), pose.position().y(), pose.position().z(),
                    pose.speed(), pose.angularSpeed(), pose.tiltDegrees());
        }
    }
}
