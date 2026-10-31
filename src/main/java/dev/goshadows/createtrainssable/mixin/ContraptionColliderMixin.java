package dev.goshadows.createtrainssable.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.slf4j.Logger;
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
 *
 * <p>{@code collideEntities} is Create's own method, so it is not remapped.</p>
 */
@Mixin(value = ContraptionCollider.class, remap = false)
public abstract class ContraptionColliderMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logged;

    static {
        LOGGER.info("Create Trains Sable: collision mixin applied; replaced carriages do not push entities twice");
    }

    @Inject(method = "collideEntities(Lcom/simibubi/create/content/contraptions/"
            + "AbstractContraptionEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void createtrainssable$skipHiddenCarriage(AbstractContraptionEntity entity,
                                                             CallbackInfo callback) {
        if (entity instanceof CarriageContraptionEntity && entity.isInvisible()) {
            if (!logged) {
                logged = true;
                LOGGER.info("Create Trains Sable: collision mixin confirmed on carriage {}", entity.getId());
            }
            callback.cancel();
        }
    }
}
