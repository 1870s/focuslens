package dev.focuslens.zoom;

import dev.focuslens.FocusLens;
import dev.focuslens.client.KeyBindings;
import dev.focuslens.config.FocusLensConfig;
import dev.focuslens.config.ZoomActivation;
import dev.focuslens.config.ZoomSensitivityMode;
import dev.focuslens.mc.McCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Zoom state machine.
 *
 * <p>Behaviour reproduced from OptiFine (as observed in game; no OptiFine code is used):
 * <ul>
 *     <li>the zoom applies only while the key is held and no screen is open;</li>
 *     <li>the final field of view (after sprint/speed/fluid effects) is divided by 4, instantly;</li>
 *     <li>the cinematic camera is active while zoomed and the player's setting is untouched afterwards;</li>
 *     <li>the first-person hand is zoomed together with the world.</li>
 * </ul>
 * Everything beyond that (toggle mode, other factors, scroll zoom, animation, other sensitivity modes)
 * is opt-in.
 *
 * <p>The state is never written into Minecraft's options. FOV and mouse handling are modified on the
 * fly through mixins, so releasing the key always restores the exact previous behaviour.
 */
public final class ZoomController {
    private static boolean toggledOn;
    private static boolean active;
    private static double targetFactor = 1.0;
    private static double currentFactor = 1.0;
    private static double scrollFactor = Double.NaN;
    private static double scrollRemainder;
    private static long lastFrameNanos;
    private static float lastWorldFov = 70.0F;
    private static float lastZoomedFov = 70.0F;
    private static long lastScrollNanos;

    private ZoomController() {
    }

    /** Called once per rendered frame, from the world-FOV hook. */
    private static void updateFrame() {
        FocusLensConfig config = FocusLens.config();
        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping zoomKey = KeyBindings.ZOOM;
        if (zoomKey == null) {
            return;
        }

        boolean pressed = false;
        while (zoomKey.consumeClick()) {
            pressed = true;
            if (config.zoomActivation == ZoomActivation.TOGGLE) {
                toggledOn = !toggledOn;
            }
        }
        boolean wanted;
        if (!config.zoomEnabled || minecraft.player == null) {
            wanted = false;
            toggledOn = false;
        } else if (config.zoomActivation == ZoomActivation.TOGGLE) {
            wanted = toggledOn;
        } else {
            toggledOn = false;
            wanted = (zoomKey.isDown() || pressed) && McCompat.currentScreen(minecraft) == null;
        }

        if (wanted && !active) {
            if (Double.isNaN(scrollFactor) || config.resetScrollZoomOnRelease) {
                scrollFactor = config.zoomFactor;
            }
            scrollRemainder = 0.0;
        }
        active = wanted;
        targetFactor = active ? (config.scrollZoom ? scrollFactor : config.zoomFactor) : 1.0;

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0 : Math.min(0.25, (now - lastFrameNanos) / 1.0e9);
        lastFrameNanos = now;
        if (config.zoomAnimation) {
            currentFactor = ZoomMath.approach(currentFactor, targetFactor, dt, config.zoomAnimationSpeed);
        } else {
            currentFactor = targetFactor;
        }
    }

    /** Hook for Camera#calculateFov: the world FOV after all vanilla modifiers. */
    public static float modifyWorldFov(float fov) {
        updateFrame();
        lastWorldFov = fov;
        float zoomed = currentFactor == 1.0 ? fov : (float) (fov / currentFactor);
        lastZoomedFov = zoomed;
        return zoomed;
    }

    /** Hook for Camera#calculateHudFov: the FOV used for the first-person hand. */
    public static float modifyHandFov(float fov) {
        if (currentFactor == 1.0 || !FocusLens.config().zoomFirstPersonHand) {
            return fov;
        }
        return (float) (fov / currentFactor);
    }

    /** True while the zoom key is engaged (not during a zoom-out animation). */
    public static boolean isActive() {
        return active;
    }

    public static boolean isZoomed() {
        return active || currentFactor > 1.0;
    }

    public static double currentFactor() {
        return currentFactor;
    }

    /** OptiFine switches on the cinematic camera while zoomed. */
    public static boolean forceCinematicCamera() {
        return active && FocusLens.config().zoomSensitivityMode == ZoomSensitivityMode.CINEMATIC;
    }

    /** Multiplier applied to the yaw/pitch change produced by the mouse. */
    public static double rotationMultiplier() {
        if (!isZoomed()) {
            return 1.0;
        }
        FocusLensConfig config = FocusLens.config();
        double multiplier = config.zoomSensitivity;
        if (config.zoomSensitivityMode == ZoomSensitivityMode.SCALED) {
            multiplier *= ZoomMath.screenSpaceSensitivity(lastWorldFov, lastZoomedFov);
        }
        return multiplier;
    }

    /**
     * Scroll wheel hook. Returns true when the scroll was used to change the zoom and must not reach
     * the hotbar.
     */
    public static boolean onScroll(double verticalAmount) {
        FocusLensConfig config = FocusLens.config();
        if (!active || !config.scrollZoom || verticalAmount == 0.0) {
            return false;
        }
        scrollRemainder += verticalAmount;
        int steps = (int) scrollRemainder;
        if (steps != 0) {
            scrollRemainder -= steps;
            scrollFactor = ZoomMath.applyScrollSteps(scrollFactor, steps, 1.0, FocusLensConfig.MAX_ZOOM_FACTOR);
            lastScrollNanos = System.nanoTime();
        }
        return true;
    }

    /** True for a short time after the scroll wheel changed the zoom level (for the HUD indicator). */
    public static boolean recentlyScrolled() {
        return active && lastScrollNanos != 0L && System.nanoTime() - lastScrollNanos < 1_200_000_000L;
    }

    /** Clears all state, e.g. when leaving a world. */
    public static void reset() {
        toggledOn = false;
        active = false;
        targetFactor = 1.0;
        currentFactor = 1.0;
        scrollFactor = Double.NaN;
        lastFrameNanos = 0L;
    }
}
