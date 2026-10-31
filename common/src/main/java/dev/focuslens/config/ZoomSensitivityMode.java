package dev.focuslens.config;

public enum ZoomSensitivityMode {
    /**
     * OptiFine behaviour: the cinematic camera is switched on while zoomed, which smooths and damps
     * mouse movement. The player's own cinematic-camera setting is never modified.
     */
    CINEMATIC("Cinematic (OptiFine)"),
    /**
     * Rotation is scaled by tan(zoomedFov / 2) / tan(fov / 2), so the image moves across the screen at
     * the same speed as without zoom. No smoothing.
     */
    SCALED("Scaled to FOV"),
    /** Raw vanilla mouse handling, only the sensitivity multiplier applies. */
    UNCHANGED("Unchanged");

    public final String label;

    ZoomSensitivityMode(String label) {
        this.label = label;
    }
}
