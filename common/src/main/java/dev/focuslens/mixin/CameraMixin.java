package dev.focuslens.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.focuslens.zoom.ZoomController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the zoom to the final field of view, after sprinting, speed effects, death and fluid
 * modifiers. Those modifiers are all multiplicative, so dividing the result is the same as OptiFine
 * dividing the FOV and keeps every vanilla effect intact while zoomed.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float focuslens$zoomWorldFov(float fov) {
        return ZoomController.modifyWorldFov(fov);
    }

    @ModifyReturnValue(method = "calculateHudFov", at = @At("RETURN"))
    private float focuslens$zoomHandFov(float fov) {
        return ZoomController.modifyHandFov(fov);
    }
}
