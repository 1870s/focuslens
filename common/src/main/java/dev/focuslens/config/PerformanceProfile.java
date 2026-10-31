package dev.focuslens.config;

/**
 * Presets for the performance settings. All presets keep the lossless culling on; they differ only in
 * the distance limits, which are the settings that trade visible detail for speed.
 */
public enum PerformanceProfile {
    /** Only optimizations that never change what is visible. */
    DEFAULT("Default", 0, 0, 0),
    /** Also hides small details that are far away. */
    BALANCED("Balanced", 48, 48, 48),
    /** Shorter limits for low-end hardware. */
    PERFORMANCE("Performance", 32, 32, 24),
    /** Set automatically when a single setting is changed by hand. */
    CUSTOM("Custom", 0, 0, 0);

    public final String label;
    public final int blockEntityDistance;
    public final int decorativeEntityDistance;
    public final int particleDistance;

    PerformanceProfile(String label, int blockEntityDistance, int decorativeEntityDistance, int particleDistance) {
        this.label = label;
        this.blockEntityDistance = blockEntityDistance;
        this.decorativeEntityDistance = decorativeEntityDistance;
        this.particleDistance = particleDistance;
    }
}
