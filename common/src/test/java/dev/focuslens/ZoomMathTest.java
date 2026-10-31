package dev.focuslens;

import dev.focuslens.zoom.ZoomMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoomMathTest {
    @Test
    void approachIsFrameRateIndependent() {
        // 1 second at 30 FPS and at 240 FPS must end at the same value.
        double a = 1.0, b = 1.0;
        for (int i = 0; i < 30; i++) a = ZoomMath.approach(a, 4.0, 1.0 / 30, 5.0);
        for (int i = 0; i < 240; i++) b = ZoomMath.approach(b, 4.0, 1.0 / 240, 5.0);
        assertEquals(a, b, 1e-3);
        assertTrue(a > 3.9 && a <= 4.0);
    }

    @Test
    void approachSnapsToTarget() {
        assertEquals(4.0, ZoomMath.approach(3.9999, 4.0, 0.016, 14.0), 0.0);
        assertEquals(2.0, ZoomMath.approach(2.0, 2.0, 0.0, 14.0), 0.0);
    }

    @Test
    void screenSpaceSensitivityMatchesTangentRatio() {
        // 70 degrees divided by 4 (OptiFine): tan(8.75 deg) / tan(35 deg).
        double expected = Math.tan(Math.toRadians(8.75)) / Math.tan(Math.toRadians(35));
        assertEquals(expected, ZoomMath.screenSpaceSensitivity(70, 17.5), 1e-12);
        assertEquals(1.0, ZoomMath.screenSpaceSensitivity(70, 70), 0.0);
    }

    @Test
    void scrollStepsAreClamped() {
        assertEquals(4.0 * 1.2 * 1.2, ZoomMath.applyScrollSteps(4.0, 2, 1.0, 50.0), 1e-12);
        assertEquals(50.0, ZoomMath.applyScrollSteps(40.0, 5, 1.0, 50.0), 0.0);
        assertEquals(1.0, ZoomMath.applyScrollSteps(1.1, -3, 1.0, 50.0), 0.0);
    }
}
