package dev.focuslens.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.focuslens.culling.CullingManager;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds occlusion culling to vanilla's per-entity visibility test. Matches shouldRender by name only:
 * 26.3 added a partialTicks parameter, and the entity is captured by type, so one injector fits all
 * supported versions.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean focuslens$occlusionCull(boolean visible, @Local(argsOnly = true) Entity entity) {
        return visible && !CullingManager.shouldHideEntity(entity);
    }
}
