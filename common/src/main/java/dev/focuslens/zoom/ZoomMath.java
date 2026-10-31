package dev.focuslens.zoom;

/**
 * Pure math used by the zoom, kept free of Minecraft types so it can be unit tested.
 */
public final class ZoomMath {
    /** Each scroll step multiplies or divides the zoom factor by this amount. */
    public static final double SCROLL_STEP = 1.2;

    private ZoomMath() {
    }

    /**
     * Frame-rate independent exponential approach of {@code current} towards {@code target}.
     *
     * @param rate approach rate in 1/s; after {@code 1/rate} seconds 63% of the distance is covered
     */
    public static double approach(double current, double target, double dtSeconds, double rate) {
        if (dtSeconds <= 0.0) {
            return current;
        }
        double t = 1.0 - Math.exp(-dtSeconds * rate);
        double next = current + (target - current) * t;
        // Snap when visually indistinguishable so "zoom finished" is reachable in finite time.
        return Math.abs(next - target) < 1.0e-3 * target ? target : next;
    }

    /**
     * Ratio by which mouse rotation must be scaled so the picture moves across the screen at the same
     * speed with and without zoom. Uses the vertical FOV of the perspective projection.
     */
    public static double screenSpaceSensitivity(double baseFovDegrees, double zoomedFovDegrees) {
        if (baseFovDegrees <= 0.0 || zoomedFovDegrees <= 0.0 || zoomedFovDegrees >= baseFovDegrees) {
            return 1.0;
        }
        double base = Math.tan(Math.toRadians(baseFovDegrees) / 2.0);
        double zoomed = Math.tan(Math.toRadians(zoomedFovDegrees) / 2.0);
        return zoomed / base;
    }

    /** Applies whole scroll steps to a zoom factor and clamps the result. Positive steps zoom in. */
    public static double applyScrollSteps(double factor, int steps, double min, double max) {
        double result = factor * Math.pow(SCROLL_STEP, steps);
        return Math.max(min, Math.min(max, result));
    }
}
