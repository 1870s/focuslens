package dev.focuslens.culling;

/**
 * Per-frame counters of objects that FocusLens kept from being drawn. Main thread only. The values of
 * the last complete frame are exposed for the overlay and the benchmark.
 */
public final class CullingStats {
    public static final int ENTITY_SECTION = 0;
    public static final int ENTITY_RAYCAST = 1;
    public static final int ENTITY_DISTANCE = 2;
    public static final int BLOCK_ENTITY_FRUSTUM = 3;
    public static final int BLOCK_ENTITY_RAYCAST = 4;
    public static final int BLOCK_ENTITY_DISTANCE = 5;
    public static final int PARTICLE_OCCLUSION = 6;
    public static final int PARTICLE_DISTANCE = 7;
    public static final int COUNT = 8;

    private static final int[] current = new int[COUNT];
    private static final int[] last = new int[COUNT];

    private CullingStats() {
    }

    public static void increment(int counter) {
        current[counter]++;
    }

    /** Called at the start of each frame. */
    public static void endFrame() {
        System.arraycopy(current, 0, last, 0, COUNT);
        java.util.Arrays.fill(current, 0);
    }

    public static int last(int counter) {
        return last[counter];
    }

    public static int lastEntities() {
        return last[ENTITY_SECTION] + last[ENTITY_RAYCAST] + last[ENTITY_DISTANCE];
    }

    public static int lastBlockEntities() {
        return last[BLOCK_ENTITY_FRUSTUM] + last[BLOCK_ENTITY_RAYCAST] + last[BLOCK_ENTITY_DISTANCE];
    }

    public static int lastParticles() {
        return last[PARTICLE_OCCLUSION] + last[PARTICLE_DISTANCE];
    }
}
