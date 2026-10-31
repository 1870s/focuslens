package dev.focuslens.config;

public enum ZoomActivation {
    /** Zoom while the key is held (OptiFine behaviour). */
    HOLD("Hold"),
    /** Press once to zoom in, press again to zoom out. */
    TOGGLE("Toggle");

    public final String label;

    ZoomActivation(String label) {
        this.label = label;
    }
}
