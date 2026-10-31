package dev.focuslens.culling;

/**
 * Line-of-sight tests through a voxel grid (Amanatides &amp; Woo, "A Fast Voxel Traversal Algorithm",
 * 1987). Pure Java, no Minecraft types, unit tested.
 *
 * <p>The design is deliberately conservative: anything unexpected (a ray that exceeds its step
 * budget, a camera inside the box, a camera inside an opaque block) counts as "visible". A false
 * "visible" only costs a little performance; a false "hidden" would make something vanish.
 */
public final class VoxelRaycaster {
    /** Rays longer than this many voxel steps are treated as visible. */
    public static final int MAX_STEPS = 512;
    private static final double EDGE_INSET = 1.0e-3;

    private VoxelRaycaster() {
    }

    /**
     * Returns true when no opaque voxel lies strictly between the start voxel and the target voxel.
     * The start voxel (the camera) and the target voxel itself are never tested.
     */
    public static boolean isPathClear(OpacityGrid grid, double sx, double sy, double sz, double tx, double ty, double tz) {
        int x = floor(sx);
        int y = floor(sy);
        int z = floor(sz);
        final int endX = floor(tx);
        final int endY = floor(ty);
        final int endZ = floor(tz);

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;

        int stepX = Double.compare(dx, 0.0);
        int stepY = Double.compare(dy, 0.0);
        int stepZ = Double.compare(dz, 0.0);

        // Distance along the segment (t in [0, 1]) to cross one voxel on each axis.
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        // Distance along the segment to the first voxel boundary on each axis.
        double tMaxX = stepX > 0 ? (x + 1 - sx) / dx : stepX < 0 ? (sx - x) / -dx : Double.POSITIVE_INFINITY;
        double tMaxY = stepY > 0 ? (y + 1 - sy) / dy : stepY < 0 ? (sy - y) / -dy : Double.POSITIVE_INFINITY;
        double tMaxZ = stepZ > 0 ? (z + 1 - sz) / dz : stepZ < 0 ? (sz - z) / -dz : Double.POSITIVE_INFINITY;

        for (int i = 0; i < MAX_STEPS; i++) {
            if (x == endX && y == endY && z == endZ) {
                return true;
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    if (tMaxX > 1.0) return true;
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    if (tMaxZ > 1.0) return true;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1.0) return true;
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1.0) return true;
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) {
                return true;
            }
            if (grid.isOpaque(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true when any of the sample points of the box (its centre and its eight corners) can be
     * seen from the camera. The centre is tested first because it is the most likely to be visible.
     */
    public static boolean isBoxVisible(OpacityGrid grid, double camX, double camY, double camZ,
                                       double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (camX >= minX && camX <= maxX && camY >= minY && camY <= maxY && camZ >= minZ && camZ <= maxZ) {
            return true;
        }
        if (isPathClear(grid, camX, camY, camZ, (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5)) {
            return true;
        }
        // Pull corners a hair inside the box so they never sit exactly on a voxel boundary.
        double x0 = minX + EDGE_INSET, x1 = maxX - EDGE_INSET;
        double y0 = minY + EDGE_INSET, y1 = maxY - EDGE_INSET;
        double z0 = minZ + EDGE_INSET, z1 = maxZ - EDGE_INSET;
        return isPathClear(grid, camX, camY, camZ, x0, y0, z0)
                || isPathClear(grid, camX, camY, camZ, x1, y0, z0)
                || isPathClear(grid, camX, camY, camZ, x0, y1, z0)
                || isPathClear(grid, camX, camY, camZ, x1, y1, z0)
                || isPathClear(grid, camX, camY, camZ, x0, y0, z1)
                || isPathClear(grid, camX, camY, camZ, x1, y0, z1)
                || isPathClear(grid, camX, camY, camZ, x0, y1, z1)
                || isPathClear(grid, camX, camY, camZ, x1, y1, z1);
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
