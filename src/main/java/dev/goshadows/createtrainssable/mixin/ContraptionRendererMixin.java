package dev.goshadows.createtrainssable.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.renderer.culling.Frustum;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips Create's own renderer for carriages that this mod has replaced with a Sable structure.
 *
 * <p>Create's contraption renderers never consult {@code Entity#isInvisible()}, so a hidden
 * carriage kept drawing its block entities, its actors and its shadow next to the sub-level. This
 * is the vanilla half of the duplicate; the blocks themselves are drawn by Flywheel and are handled
 * by {@link FlywheelVisualHelperMixin}.</p>
 *
 * <p>{@code remap} stays on: {@code shouldRender} is an override of a Minecraft method, so its name
 * in a production jar is the obfuscated one. Targeting the mod method name with {@code remap = false}
 * is exactly how this injection silently did nothing before.</p>
 */
@Mixin(value = ContraptionEntityRenderer.class, remap = true)
public abstract class ContraptionRendererMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logged;

    static {
        LOGGER.info("Create Trains Sable: render mixin applied; replaced carriages are not drawn by Create");
    }

    @Inject(method = "shouldRender(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;"
            + "Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void createtrainssable$skipHiddenCarriage(AbstractContraptionEntity entity,
                                                      Frustum frustum, double cameraX,
                                                      double cameraY, double cameraZ,
                                                      CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof CarriageContraptionEntity && entity.isInvisible()) {
            if (!logged) {
                logged = true;
                LOGGER.info("Create Trains Sable: render mixin confirmed on carriage {}", entity.getId());
            }
            callback.setReturnValue(false);
        }
    }
}
