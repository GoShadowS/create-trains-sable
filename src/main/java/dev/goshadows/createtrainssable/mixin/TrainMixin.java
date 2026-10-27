package dev.goshadows.createtrainssable.mixin;

import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createtrainssable.CrashSignals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create nulls Train.graph inside crash(), which destroys the travelling-point state this mod
 * needs for a later resync. Capturing it at HEAD is the only place where the graph is still
 * intact. No behaviour is changed: this injection only records a snapshot.
 */
@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin {

    @Inject(method = "crash()V", at = @At("HEAD"))
    private void createtrainssable$beforeCrash(CallbackInfo ci) {
        CrashSignals.onCreateCrash((Train) (Object) this);
    }
}
