package dev.goshadows.createtrainssable.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a replaced carriage from pushing entities around next to its Sable structure.
 *
 * <p>The contraption entity stays alive because it owns the schedule, the seats and the
 * inventory, but its collision is now redundant: the sub-level is the solid object. Leaving both
 * active made trains shove players twice, from two slightly different positions.</p>
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.ContraptionCollider", remap = false)
public abstract class ContraptionColliderMixin {

    @Inject(method = "collideEntities(Lcom/simibubi/create/content/contraptions/"
            + "AbstractContraptionEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void createtrainssable$skipHiddenCarriage(AbstractContraptionEntity entity,
                                                             CallbackInfo callback) {
        if (entity instanceof CarriageContraptionEntity && entity.isInvisible()) {
            callback.cancel();
        }
    }
}
