package dev.focuslens.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Frustum.class)
public interface FrustumAccessor {
    /** Allocation-free box test; the public variants need an AABB or BoundingBox object. */
    @Invoker("cubeInFrustum")
    int focuslens$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
