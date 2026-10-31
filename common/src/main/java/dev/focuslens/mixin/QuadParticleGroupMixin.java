package dev.focuslens.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.focuslens.culling.CullingManager;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla only frustum-tests particles; this adds the section occlusion test and the distance limit. */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
    private boolean focuslens$cullParticle(Frustum frustum, double x, double y, double z, Operation<Boolean> original) {
        return original.call(frustum, x, y, z) && !CullingManager.shouldHideParticle(x, y, z);
    }
}
