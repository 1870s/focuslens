package dev.focuslens.culling;

import dev.focuslens.FocusLens;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Ray-traced occlusion culling for entities and block entities, computed on one background thread.
 *
 * <p>How it works:
 * <ol>
 *     <li>During a frame, every entity/block entity that vanilla would draw is collected as a candidate
 *     (main thread, no allocation: two lists are reused alternately).</li>
 *     <li>At the start of the next frame the candidates and the camera position are handed to the
 *     worker, but only if it is idle. A busy worker is never queued up, so it cannot fall behind.</li>
 *     <li>The worker casts rays from the camera to each candidate's box and stores the result on the
 *     object itself ({@link Cullable}).</li>
 *     <li>The main thread hides an object only if a recent, completed pass found it occluded and the
 *     camera has not moved away from where that pass was computed.</li>
 * </ol>
 * The main thread never waits for the worker. Results are at most one or two frames old, and objects
 * the worker has not seen yet are always drawn.
 */
public final class OcclusionCuller {
    public static final OcclusionCuller INSTANCE = new OcclusionCuller();

    /** Results computed further away than this from the current camera are ignored. */
    private static final double MAX_CAMERA_DRIFT_SQ = 0.75 * 0.75;
    /** Results older than this are ignored (e.g. the game was paused or the worker stalled). */
    private static final long MAX_RESULT_AGE_NANOS = 250_000_000L;
    /** Objects larger than this are always drawn; their boxes are too coarse to test reliably. */
    private static final double MAX_BOX_SIZE = 12.0;

    private ArrayList<Object> collecting = new ArrayList<>(512);
    private ArrayList<Object> spare = new ArrayList<>(512);
    private final AtomicReference<Snapshot> pending = new AtomicReference<>();
    private volatile boolean workerBusy;
    private volatile boolean running;
    private Thread worker;

    private long nextPassId;
    private volatile long completedPass = -1L;
    private volatile long completedAtNanos;
    private volatile double completedCamX, completedCamY, completedCamZ;
    private double publishedCamX, publishedCamY, publishedCamZ;

    private boolean frameActive;
    private ClientLevel frameLevel;
    private double frameCamX, frameCamY, frameCamZ;
    private boolean frameCameraKnown;

    private OcclusionCuller() {
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::workerLoop, "FocusLens Occlusion Culling");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /**
     * Main thread, once per frame before the level is extracted: hands last frame's candidates to the
     * worker and starts collecting new ones.
     */
    public void beginFrame(ClientLevel level, boolean enabled) {
        if (frameActive && frameCameraKnown && frameLevel != null && !collecting.isEmpty() && !workerBusy && pending.get() == null) {
            ArrayList<Object> handOff = collecting;
            collecting = spare;
            spare = handOff;
            long passId = nextPassId++;
            publishedCamX = frameCamX;
            publishedCamY = frameCamY;
            publishedCamZ = frameCamZ;
            workerBusy = true;
            pending.set(new Snapshot(passId, frameLevel, frameCamX, frameCamY, frameCamZ, handOff));
            LockSupport.unpark(worker);
        }
        collecting.clear();
        frameActive = enabled && level != null;
        frameLevel = level;
        frameCameraKnown = false;
        if (level == null) {
            completedPass = -1L;
        }
    }

    /** Main thread: the camera position used for this frame's queries. */
    public void setFrameCamera(double x, double y, double z) {
        frameCamX = x;
        frameCamY = y;
        frameCamZ = z;
        frameCameraKnown = true;
    }

    public boolean isFrameCameraKnown() {
        return frameCameraKnown;
    }

    /**
     * Main thread: registers the object for the next pass and returns whether the latest results say
     * it is hidden.
     */
    public boolean isOccluded(Cullable target) {
        if (!frameActive || !frameCameraKnown) {
            return false;
        }
        collecting.add(target);
        long occludedPass = target.focuslens$getOccludedPass();
        long completed = completedPass;
        if (occludedPass < 0L || completed < 0L || occludedPass < completed) {
            return false;
        }
        if (System.nanoTime() - completedAtNanos > MAX_RESULT_AGE_NANOS) {
            return false;
        }
        return distanceSq(frameCamX, frameCamY, frameCamZ, completedCamX, completedCamY, completedCamZ) < MAX_CAMERA_DRIFT_SQ
                && distanceSq(frameCamX, frameCamY, frameCamZ, publishedCamX, publishedCamY, publishedCamZ) < MAX_CAMERA_DRIFT_SQ;
    }

    private void workerLoop() {
        while (running) {
            Snapshot snapshot = pending.getAndSet(null);
            if (snapshot == null) {
                LockSupport.parkNanos(this, 50_000_000L);
                continue;
            }
            try {
                process(snapshot);
            } catch (Throwable t) {
                // Never let a bad pass kill the thread; mark everything visible instead.
                FocusLens.LOG.error("Occlusion culling pass failed; objects of this pass stay visible", t);
                for (Object target : snapshot.targets) {
                    ((Cullable) target).focuslens$setOccludedPass(-1L);
                }
            } finally {
                snapshot.targets.clear();
                workerBusy = false;
            }
        }
    }

    private void process(Snapshot snapshot) {
        LevelOpacity opacity = new LevelOpacity(snapshot.level);
        double camX = snapshot.camX;
        double camY = snapshot.camY;
        double camZ = snapshot.camZ;
        // Camera inside an opaque block (spectator mode): every ray would be blocked, so cull nothing.
        boolean cameraInsideBlock = opacity.isOpaque(floor(camX), floor(camY), floor(camZ));
        long passId = snapshot.passId;

        for (Object target : snapshot.targets) {
            Cullable cullable = (Cullable) target;
            boolean visible = cameraInsideBlock || isVisible(opacity, target, camX, camY, camZ);
            cullable.focuslens$setOccludedPass(visible ? -1L : passId);
        }

        completedCamX = camX;
        completedCamY = camY;
        completedCamZ = camZ;
        completedAtNanos = System.nanoTime();
        completedPass = passId;
    }

    private static boolean isVisible(OpacityGrid grid, Object target, double camX, double camY, double camZ) {
        double minX, minY, minZ, maxX, maxY, maxZ;
        if (target instanceof Entity entity) {
            AABB box = entity.getBoundingBox();
            if (box.getXsize() > MAX_BOX_SIZE || box.getYsize() > MAX_BOX_SIZE || box.getZsize() > MAX_BOX_SIZE) {
                return true;
            }
            // Margin for render-only parts outside the hitbox (held items, wings, flames).
            minX = box.minX - 0.5;
            minY = box.minY - 0.5;
            minZ = box.minZ - 0.5;
            maxX = box.maxX + 0.5;
            maxY = box.maxY + 0.5;
            maxZ = box.maxZ + 0.5;
        } else if (target instanceof BlockEntity blockEntity) {
            BlockPos pos = blockEntity.getBlockPos();
            // Covers models taller than one block (standing banners, beds, bells).
            minX = pos.getX() - 0.5;
            minY = pos.getY() - 0.5;
            minZ = pos.getZ() - 0.5;
            maxX = pos.getX() + 1.5;
            maxY = pos.getY() + 2.0;
            maxZ = pos.getZ() + 1.5;
        } else {
            return true;
        }
        return VoxelRaycaster.isBoxVisible(grid, camX, camY, camZ, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double distanceSq(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private record Snapshot(long passId, ClientLevel level, double camX, double camY, double camZ, ArrayList<Object> targets) {
    }
}
