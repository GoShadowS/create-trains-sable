package dev.goshadows.createtrainssable.mixin;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Flywheel from building a contraption visual for carriages that this mod has replaced.
 *
 * <p>Create renders contraptions through Flywheel, and Flywheel's {@code EntityStorage} accepts
 * every entity that has a visualizer, invisible or not. The contraption visual therefore kept
 * drawing the carriage blocks and the bogeys on top of the Sable structure. Denying a visual for
 * an invisible carriage removes the duplicate without touching any block data.</p>
 */
@Mixin(targets = "dev.engine_room.flywheel.lib.visualization.VisualizationHelper", remap = false)
public abstract class FlywheelVisualHelperMixin {

    @Inject(method = "canVisualize(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void createtrainssable$skipHiddenCarriage(Entity entity,
                                                             CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof CarriageContraptionEntity && entity.isInvisible()) {
            callback.setReturnValue(false);
        }
    }
}
