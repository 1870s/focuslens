package dev.focuslens.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.focuslens.culling.BlockEntityCullingHook;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge keeps vanilla's tryExtractRenderState as a deprecated wrapper and adds an overload with a
 * trailing Frustum parameter, which is the one the level extractor calls. A plain name selector would
 * pick the first (deprecated) overload, so the overload is selected by descriptor instead. NeoForge
 * already frustum-tests each block entity there, so only FocusLens' ray and distance tests run.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "name=/^tryExtractRenderState$/ desc=/Frustum;\\)/", at = @At("HEAD"), cancellable = true)
    private void focuslens$cull(CallbackInfoReturnable<Object> cir, @Local(argsOnly = true) BlockEntity blockEntity) {
        if (BlockEntityCullingHook.shouldSkip((BlockEntityRenderDispatcher) (Object) this, blockEntity, false)) {
            cir.setReturnValue(null);
        }
    }
}
