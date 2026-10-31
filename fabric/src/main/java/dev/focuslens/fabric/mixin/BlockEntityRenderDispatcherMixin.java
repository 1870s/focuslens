package dev.focuslens.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.focuslens.culling.BlockEntityCullingHook;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla has exactly one tryExtractRenderState (3 parameters in 26.1, 4 since 26.2), so the name is
 * unambiguous; the block entity is captured by type to cover both signatures.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void focuslens$cull(CallbackInfoReturnable<Object> cir, @Local(argsOnly = true) BlockEntity blockEntity) {
        if (BlockEntityCullingHook.shouldSkip((BlockEntityRenderDispatcher) (Object) this, blockEntity, true)) {
            cir.setReturnValue(null);
        }
    }
}
