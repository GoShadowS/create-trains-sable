package dev.goshadows.createtrainssable.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Flywheel from building a contraption visual for carriages that this mod has replaced.
 *
 * <p>This is the half that matters: with Create 6 a contraption's blocks and its bogeys are drawn
 * by Flywheel, not by the entity renderer, and Flywheel's {@code EntityStorage} accepts every entity
 * that has a visualizer - invisible or not. The contraption visual therefore kept drawing the
 * carriage on top of the Sable structure. Denying a visual to an invisible carriage removes the
 * duplicate without touching any block data, and it is also what stops the bogeys from being drawn
 * twice.</p>
 *
 * <p>{@code canVisualize} is Flywheel's own method, not a Minecraft override, so it is not remapped
 * and the injection names it directly.</p>
 */
@Mixin(value = VisualizationHelper.class, remap = false)
public abstract class FlywheelVisualHelperMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logged;

    static {
        LOGGER.info("Create Trains Sable: Flywheel mixin applied; replaced carriages get no contraption visual");
    }

    @Inject(method = "canVisualize(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void createtrainssable$skipHiddenCarriage(Entity entity,
                                                             CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof CarriageContraptionEntity && entity.isInvisible()) {
            if (!logged) {
                logged = true;
                LOGGER.info("Create Trains Sable: Flywheel mixin confirmed on carriage {}", entity.getId());
            }
            callback.setReturnValue(false);
        }
    }
}
