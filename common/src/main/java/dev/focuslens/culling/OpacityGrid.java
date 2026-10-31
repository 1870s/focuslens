package dev.focuslens.culling;

/** A voxel grid that answers "does this block fully block sight?". */
@FunctionalInterface
public interface OpacityGrid {
    boolean isOpaque(int x, int y, int z);
}
