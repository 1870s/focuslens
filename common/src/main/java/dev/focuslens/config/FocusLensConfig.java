package dev.focuslens.config;

/**
 * Plain settings object, serialized to {@code config/focuslens.json} with Gson. Defaults reproduce
 * OptiFine's zoom and enable only optimizations that do not change what is drawn.
 */
public final class FocusLensConfig {
    public static final int CURRENT_VERSION = 1;

    public static final double MIN_ZOOM_FACTOR = 1.5;
    public static final double MAX_ZOOM_FACTOR = 50.0;

    public int configVersion = CURRENT_VERSION;

    // Zoom
    public boolean zoomEnabled = true;
    public ZoomActivation zoomActivation = ZoomActivation.HOLD;
    /** OptiFine divides the field of view by 4. */
    public double zoomFactor = 4.0;
    public ZoomSensitivityMode zoomSensitivityMode = ZoomSensitivityMode.CINEMATIC;
    /** Extra multiplier on mouse rotation while zoomed (1.0 = unchanged). */
    public double zoomSensitivity = 1.0;
    public boolean scrollZoom = false;
    public boolean resetScrollZoomOnRelease = true;
    public boolean zoomAnimation = false;
    /** Exponential approach rate in 1/s; higher is faster. */
    public double zoomAnimationSpeed = 14.0;
    /** OptiFine applies the zoom to the first-person hand as well. */
    public boolean zoomFirstPersonHand = true;
    public boolean showZoomLevel = true;

    // Performance
    public PerformanceProfile performanceProfile = PerformanceProfile.DEFAULT;
    public boolean entitySectionCulling = true;
    public boolean entityRaycastCulling = true;
    public boolean blockEntityRaycastCulling = true;
    public boolean blockEntityFrustumCulling = true;
    public boolean particleOcclusionCulling = true;
    public boolean keepNameTagsVisible = true;
    /** 0 = vanilla behaviour. */
    public int blockEntityDistanceLimit = 0;
    public int decorativeEntityDistanceLimit = 0;
    public int particleDistanceLimit = 0;

    // General
    public boolean showPerformanceOverlay = false;
    public int benchmarkSeconds = 30;

    public void applyProfile(PerformanceProfile profile) {
        performanceProfile = profile;
        if (profile == PerformanceProfile.CUSTOM) {
            return;
        }
        entitySectionCulling = true;
        entityRaycastCulling = true;
        blockEntityRaycastCulling = true;
        blockEntityFrustumCulling = true;
        particleOcclusionCulling = true;
        keepNameTagsVisible = true;
        blockEntityDistanceLimit = profile.blockEntityDistance;
        decorativeEntityDistanceLimit = profile.decorativeEntityDistance;
        particleDistanceLimit = profile.particleDistance;
    }

    /** Clamps every value into its legal range so a hand-edited file cannot break the game. */
    public void validate() {
        if (zoomActivation == null) zoomActivation = ZoomActivation.HOLD;
        if (zoomSensitivityMode == null) zoomSensitivityMode = ZoomSensitivityMode.CINEMATIC;
        if (performanceProfile == null) performanceProfile = PerformanceProfile.DEFAULT;
        zoomFactor = clamp(zoomFactor, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);
        zoomSensitivity = clamp(zoomSensitivity, 0.05, 2.0);
        zoomAnimationSpeed = clamp(zoomAnimationSpeed, 2.0, 40.0);
        blockEntityDistanceLimit = clampDistance(blockEntityDistanceLimit);
        decorativeEntityDistanceLimit = clampDistance(decorativeEntityDistanceLimit);
        particleDistanceLimit = clampDistance(particleDistanceLimit);
        benchmarkSeconds = (int) clamp(benchmarkSeconds, 10, 300);
        configVersion = CURRENT_VERSION;
    }

    /** A distance limit is either 0 (off) or 8..256 blocks. */
    private static int clampDistance(int value) {
        if (value <= 0) return 0;
        return (int) clamp(value, 8, 256);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
