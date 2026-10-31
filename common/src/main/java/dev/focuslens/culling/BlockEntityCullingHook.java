package dev.focuslens.culling;

import dev.focuslens.mc.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shared body of the block-entity extraction hook. The mixins live in the loader modules because the
 * hooked method differs: vanilla/Fabric have one {@code tryExtractRenderState}, NeoForge adds an overload
 * with a {@link Frustum} parameter (the one the game actually calls) next to a deprecated wrapper.
 */
public final class BlockEntityCullingHook {
    private BlockEntityCullingHook() {
    }

    /**
     * @param testFrustum whether FocusLens should test the frustum itself (false on NeoForge, which
     *                    already tests each block entity against its render bounding box)
     * @return true when the block entity must not be extracted this frame
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean shouldSkip(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, boolean testFrustum) {
        if (CullingManager.isSuspended()) {
            return false;
        }
        BlockEntityRenderer renderer = dispatcher.getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen()) {
            return false; // beacon beams, structure blocks, end gateways: drawn from far away on purpose
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = McCompat.cameraPosition(minecraft);
        if (camera == null || !renderer.shouldRender(blockEntity, camera)) {
            return false; // vanilla rejects it right after this (view distance); no point in ray tests
        }
        Frustum frustum = testFrustum ? McCompat.mainCamera(minecraft).getCullFrustum() : null;
        return CullingManager.shouldHideBlockEntity(blockEntity, frustum);
    }
}
