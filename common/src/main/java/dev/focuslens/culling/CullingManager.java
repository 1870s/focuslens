package dev.focuslens.culling;

import dev.focuslens.FocusLens;
import dev.focuslens.compat.IrisCompat;
import dev.focuslens.compat.ModCompat;
import dev.focuslens.config.FocusLensConfig;
import dev.focuslens.mc.McCompat;
import dev.focuslens.mixin.FrustumAccessor;
import dev.focuslens.mixin.LevelRendererAccessor;
import dev.focuslens.mixin.ViewAreaAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;

/**
 * All culling decisions made by the render hooks. Every method answers "should FocusLens hide this in
 * addition to what vanilla already hides?" and returns false whenever in doubt.
 *
 * <p>The section test uses the result of vanilla's own cave-culling graph
 * ({@link SectionOcclusionGraph}): a section that the graph search could not reach from the camera
 * cannot be seen, which is exactly how vanilla already culls terrain and block entities. Vanilla does
 * not apply it to entities or particles; this class does.
 */
public final class CullingManager {
    /** Set by the benchmark to measure the game with every FocusLens optimization switched off. */
    private static boolean suspended;

    private static boolean sodium;
    private static boolean entityCullingMod;
    private static boolean frameUsesSectionGraph;
    private static boolean frameShadowPass;
    private static double camX, camY, camZ;
    private static boolean cameraKnown;

    private static long cachedSectionKey = Long.MIN_VALUE;
    private static boolean cachedSectionReachable;
    private static final BlockPos.MutableBlockPos SCRATCH_POS = new BlockPos.MutableBlockPos();

    private CullingManager() {
    }

    public static void init() {
        sodium = ModCompat.sodium();
        entityCullingMod = ModCompat.entityCulling();
    }

    public static boolean isSuspended() {
        return suspended;
    }

    public static void setSuspended(boolean value) {
        suspended = value;
    }

    public static boolean sodiumPresent() {
        return sodium;
    }

    public static boolean entityCullingModPresent() {
        return entityCullingMod;
    }

    /** Main thread, at the start of every frame. */
    public static void beginFrame(Minecraft minecraft) {
        CullingStats.endFrame();
        FocusLensConfig config = FocusLens.config();
        boolean raycast = !suspended && !entityCullingMod && (config.entityRaycastCulling || config.blockEntityRaycastCulling);
        OcclusionCuller.INSTANCE.beginFrame(minecraft.level, raycast);
        frameUsesSectionGraph = !suspended && !sodium;
        frameShadowPass = false;
        cameraKnown = false;
        cachedSectionKey = Long.MIN_VALUE;
    }

    private static boolean prepareCamera() {
        if (!cameraKnown) {
            Vec3 pos = McCompat.cameraPosition(Minecraft.getInstance());
            if (pos == null) {
                return false;
            }
            camX = pos.x;
            camY = pos.y;
            camZ = pos.z;
            cameraKnown = true;
            OcclusionCuller.INSTANCE.setFrameCamera(camX, camY, camZ);
        }
        // Shadow passes of shader packs look from the sun; camera-based results do not apply there.
        frameShadowPass = IrisCompat.isRenderingShadowPass();
        return !frameShadowPass;
    }

    // ---------------------------------------------------------------- entities

    public static boolean shouldHideEntity(Entity entity) {
        if (suspended) {
            return false;
        }
        FocusLensConfig config = FocusLens.config();
        if (!config.entitySectionCulling && !config.entityRaycastCulling && config.decorativeEntityDistanceLimit <= 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.getCameraEntity() || entity instanceof LocalPlayer) {
            return false;
        }
        if (!prepareCamera()) {
            return false;
        }
        if (minecraft.shouldEntityAppearGlowing(entity)) {
            return false; // outlines are drawn through walls
        }
        if (config.keepNameTagsVisible && (entity instanceof Player || entity.shouldShowName())) {
            return false; // name tags are drawn through walls
        }
        if (entity instanceof Leashable leashable && leashable.isLeashed()) {
            return false; // the leash may cross visible space
        }

        if (config.decorativeEntityDistanceLimit > 0 && isDecorative(entity)) {
            double limit = config.decorativeEntityDistanceLimit;
            if (entity.distanceToSqr(camX, camY, camZ) > limit * limit) {
                CullingStats.increment(CullingStats.ENTITY_DISTANCE);
                return true;
            }
        }

        if (config.entitySectionCulling && frameUsesSectionGraph && !isBoxInReachableSection(minecraft, entity.getBoundingBox())) {
            CullingStats.increment(CullingStats.ENTITY_SECTION);
            return true;
        }

        if (config.entityRaycastCulling && !entityCullingMod && OcclusionCuller.INSTANCE.isOccluded((Cullable) entity)) {
            CullingStats.increment(CullingStats.ENTITY_RAYCAST);
            return true;
        }
        return false;
    }

    /** Small non-living things that are pure decoration at a distance. */
    private static boolean isDecorative(Entity entity) {
        return entity instanceof ItemEntity
                || entity instanceof HangingEntity
                || entity instanceof ArmorStand
                || entity instanceof ExperienceOrb;
    }

    // ---------------------------------------------------------------- block entities

    /**
     * @param frustum when non-null, also tests the block entity against the view frustum. Vanilla only
     *                culls block entities per 16x16x16 section; NeoForge already tests each one, so the
     *                NeoForge hook passes null.
     */
    public static boolean shouldHideBlockEntity(BlockEntity blockEntity, Frustum frustum) {
        if (suspended) {
            return false;
        }
        FocusLensConfig config = FocusLens.config();
        if (!prepareCamera()) {
            return false;
        }
        BlockPos pos = blockEntity.getBlockPos();

        if (config.blockEntityDistanceLimit > 0) {
            double limit = config.blockEntityDistanceLimit;
            double dx = pos.getX() + 0.5 - camX;
            double dy = pos.getY() + 0.5 - camY;
            double dz = pos.getZ() + 0.5 - camZ;
            if (dx * dx + dy * dy + dz * dz > limit * limit) {
                CullingStats.increment(CullingStats.BLOCK_ENTITY_DISTANCE);
                return true;
            }
        }

        if (frustum != null && config.blockEntityFrustumCulling) {
            // One block of margin for models that overhang their block (banners, beds, signs on walls).
            // JOML FrustumIntersection: -2 = inside, -1 = intersecting, anything else = outside.
            int result = ((FrustumAccessor) frustum).focuslens$cubeInFrustum(
                    pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0, pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
            if (result != FrustumIntersection.INSIDE && result != FrustumIntersection.INTERSECT) {
                CullingStats.increment(CullingStats.BLOCK_ENTITY_FRUSTUM);
                return true;
            }
        }

        if (config.blockEntityRaycastCulling && !entityCullingMod && OcclusionCuller.INSTANCE.isOccluded((Cullable) blockEntity)) {
            CullingStats.increment(CullingStats.BLOCK_ENTITY_RAYCAST);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- particles

    public static boolean shouldHideParticle(double x, double y, double z) {
        if (suspended) {
            return false;
        }
        FocusLensConfig config = FocusLens.config();
        if (!config.particleOcclusionCulling && config.particleDistanceLimit <= 0) {
            return false;
        }
        if (!prepareCamera()) {
            return false;
        }
        if (config.particleDistanceLimit > 0) {
            double dx = x - camX;
            double dy = y - camY;
            double dz = z - camZ;
            double limit = config.particleDistanceLimit;
            if (dx * dx + dy * dy + dz * dz > limit * limit) {
                CullingStats.increment(CullingStats.PARTICLE_DISTANCE);
                return true;
            }
        }
        if (config.particleOcclusionCulling && frameUsesSectionGraph
                && !isSectionReachable(Minecraft.getInstance(), floor(x) >> 4, floor(y) >> 4, floor(z) >> 4)) {
            CullingStats.increment(CullingStats.PARTICLE_OCCLUSION);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- section graph

    private static boolean isBoxInReachableSection(Minecraft minecraft, AABB box) {
        int minX = floor(box.minX - 0.5) >> 4, maxX = floor(box.maxX + 0.5) >> 4;
        int minY = floor(box.minY - 0.5) >> 4, maxY = floor(box.maxY + 0.5) >> 4;
        int minZ = floor(box.minZ - 0.5) >> 4, maxZ = floor(box.maxZ + 0.5) >> 4;
        if ((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 8) {
            return true; // very large entity, do not risk it
        }
        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = minZ; sz <= maxZ; sz++) {
                    if (isSectionReachable(minecraft, sx, sy, sz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when vanilla's occlusion graph reached the section from the camera, or when that cannot be
     * determined (no view area, section outside the render distance, outside the build height).
     */
    private static boolean isSectionReachable(Minecraft minecraft, int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (key == cachedSectionKey) {
            return cachedSectionReachable;
        }
        boolean reachable = true;
        LevelRendererAccessor levelRenderer = (LevelRendererAccessor) minecraft.levelRenderer;
        ViewArea viewArea = levelRenderer.focuslens$getViewArea();
        if (viewArea != null && minecraft.level != null && !minecraft.level.isOutsideBuildHeight(sectionY << 4)) {
            SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) viewArea).focuslens$getRenderSection(key);
            if (section != null && section.getSectionNode() == key) {
                SectionOcclusionGraph graph = levelRenderer.focuslens$getSectionOcclusionGraph();
                reachable = graph.getNode(section) != null;
            }
        }
        cachedSectionKey = key;
        cachedSectionReachable = reachable;
        return reachable;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
