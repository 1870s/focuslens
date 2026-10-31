package dev.focuslens.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.focuslens.bench.BenchmarkRunner;
import dev.focuslens.zoom.ZoomController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    /**
     * Treats the cinematic camera as enabled while zoomed (OptiFine behaviour) without writing to the
     * option, so the player's own setting can never be lost. Vanilla resets its smoothing filters on the
     * first frame without cinematic camera, so leaving the zoom cannot cause a camera jump. On NeoForge
     * the same read feeds CalculatePlayerTurnEvent, which keeps other mods' turn handlers working.
     */
    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean focuslens$cinematicWhileZoomed(boolean smoothCamera) {
        return smoothCamera || ZoomController.forceCinematicCamera();
    }

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void focuslens$scaleRotation(LocalPlayer player, double yRot, double xRot, Operation<Void> original) {
        if (BenchmarkRunner.isControllingCamera()) {
            return; // the benchmark drives the camera on a fixed path
        }
        double multiplier = ZoomController.rotationMultiplier();
        original.call(player, yRot * multiplier, xRot * multiplier);
    }

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"), cancellable = true)
    private void focuslens$scrollZoom(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        if (ZoomController.onScroll(yOffset)) {
            ci.cancel();
        }
    }
}
