package dev.focuslens;

import dev.focuslens.culling.OpacityGrid;
import dev.focuslens.culling.VoxelRaycaster;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelRaycasterTest {
    private static OpacityGrid grid(int[]... opaque) {
        Set<Long> set = new HashSet<>();
        for (int[] p : opaque) set.add(key(p[0], p[1], p[2]));
        return (x, y, z) -> set.contains(key(x, y, z));
    }

    private static OpacityGrid wallAtX(int wallX) {
        return (x, y, z) -> x == wallX;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
    }

    @Test
    void emptyGridIsAlwaysClear() {
        OpacityGrid empty = (x, y, z) -> false;
        assertTrue(VoxelRaycaster.isPathClear(empty, 0.5, 0.5, 0.5, 40.5, -12.3, 7.7));
        assertTrue(VoxelRaycaster.isPathClear(empty, -3.2, 64.1, 9.9, -3.2, 64.1, 9.9));
    }

    @Test
    void wallBetweenBlocksSight() {
        assertFalse(VoxelRaycaster.isPathClear(wallAtX(5), 0.5, 0.5, 0.5, 10.5, 0.5, 0.5));
        assertFalse(VoxelRaycaster.isPathClear(wallAtX(5), 10.5, 3.5, -2.5, 0.5, 0.5, 0.5));
    }

    @Test
    void wallBehindTargetDoesNotBlock() {
        assertTrue(VoxelRaycaster.isPathClear(wallAtX(12), 0.5, 0.5, 0.5, 10.5, 0.5, 0.5));
    }

    @Test
    void startAndTargetVoxelsAreNotTested() {
        // Camera inside an opaque voxel and target inside an opaque voxel: only voxels in between count.
        OpacityGrid g = grid(new int[]{0, 0, 0}, new int[]{4, 0, 0});
        assertTrue(VoxelRaycaster.isPathClear(g, 0.5, 0.5, 0.5, 4.5, 0.5, 0.5));
    }

    @Test
    void negativeCoordinatesWork() {
        assertFalse(VoxelRaycaster.isPathClear(wallAtX(-5), -0.5, 70.5, -20.5, -10.5, 70.5, -20.5));
        assertTrue(VoxelRaycaster.isPathClear(wallAtX(-5), -0.5, 70.5, -20.5, -4.5, 70.5, -20.5));
    }

    @Test
    void diagonalRayPassesThroughGap() {
        // A single opaque voxel next to (but not on) a diagonal line must not block it.
        OpacityGrid g = grid(new int[]{3, 0, 0});
        assertTrue(VoxelRaycaster.isPathClear(g, 0.5, 0.5, 0.5, 6.5, 6.5, 0.5));
    }

    @Test
    void boxVisibleWhenAnyCornerIsVisible() {
        // Wall at x=5 from y=-10..2; the top of the target box (y up to 6) peeks over it.
        OpacityGrid g = (x, y, z) -> x == 5 && y <= 2;
        assertTrue(VoxelRaycaster.isBoxVisible(g, 0.5, 4.5, 0.5, 9.0, 0.0, 0.0, 10.0, 6.0, 1.0));
    }

    @Test
    void boxHiddenBehindFullWall() {
        assertFalse(VoxelRaycaster.isBoxVisible(wallAtX(5), 0.5, 0.5, 0.5, 9.0, 0.0, 0.0, 10.0, 2.0, 1.0));
    }

    @Test
    void cameraInsideBoxIsVisible() {
        assertTrue(VoxelRaycaster.isBoxVisible(wallAtX(5), 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 2.0, 2.0, 2.0));
    }
}
