package dev.goshadows.createtrainssable.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips Create's own renderer for carriages that this mod has replaced with a Sable structure.
 *
 * <p>Create's contraption renderers do not consult {@code Entity#isInvisible()}, so a carriage
 * placed on a sub-level kept drawing its blocks next to the sub-level: two trains in one place.
 * The injection only changes the answer for invisible carriage entities, which nothing else
 * relies on.</p>
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer",
        remap = false)
public abstract class ContraptionRendererMixin {

    @Inject(method = "shouldRender(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;"
            + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void createtrainssable$skipHiddenCarriage(AbstractContraptionEntity entity, Frustum frustum,
                                                      double cameraX, double cameraY, double cameraZ,
                                                      CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof CarriageContraptionEntity && entity.isInvisible()) {
            callback.setReturnValue(false);
        }
    }
}
