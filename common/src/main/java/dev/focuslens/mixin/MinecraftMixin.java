package dev.focuslens.mixin;

import dev.focuslens.client.FrameHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void focuslens$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
        FrameHooks.onFrameStart((Minecraft) (Object) this);
    }
}
