package dev.focuslens;

import com.google.gson.Gson;
import dev.focuslens.config.FocusLensConfig;
import dev.focuslens.config.PerformanceProfile;
import dev.focuslens.config.ZoomActivation;
import dev.focuslens.config.ZoomSensitivityMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @Test
    void defaultsReproduceOptiFine() {
        FocusLensConfig c = new FocusLensConfig();
        assertEquals(4.0, c.zoomFactor, 0.0);
        assertEquals(ZoomActivation.HOLD, c.zoomActivation);
        assertEquals(ZoomSensitivityMode.CINEMATIC, c.zoomSensitivityMode);
        assertEquals(false, c.zoomAnimation);
        assertEquals(false, c.scrollZoom);
        assertEquals(true, c.zoomFirstPersonHand);
        assertEquals(PerformanceProfile.DEFAULT, c.performanceProfile);
        assertEquals(0, c.blockEntityDistanceLimit);
    }

    @Test
    void validateClampsHandEditedValues() {
        FocusLensConfig c = new Gson().fromJson("{\"zoomFactor\": 9000, \"zoomSensitivity\": -1, \"particleDistanceLimit\": 3,"
                + "\"blockEntityDistanceLimit\": 100000, \"zoomActivation\": null, \"benchmarkSeconds\": 1}", FocusLensConfig.class);
        c.validate();
        assertEquals(FocusLensConfig.MAX_ZOOM_FACTOR, c.zoomFactor, 0.0);
        assertEquals(0.05, c.zoomSensitivity, 0.0);
        assertEquals(8, c.particleDistanceLimit);
        assertEquals(256, c.blockEntityDistanceLimit);
        assertEquals(ZoomActivation.HOLD, c.zoomActivation);
        assertEquals(10, c.benchmarkSeconds);
    }

    @Test
    void unknownEnumFallsBackToDefault() {
        FocusLensConfig c = new Gson().fromJson("{\"zoomSensitivityMode\": \"WARP_SPEED\"}", FocusLensConfig.class);
        c.validate();
        assertEquals(ZoomSensitivityMode.CINEMATIC, c.zoomSensitivityMode);
    }

    @Test
    void profilesOnlyChangeDistanceLimits() {
        FocusLensConfig c = new FocusLensConfig();
        c.entityRaycastCulling = false;
        c.applyProfile(PerformanceProfile.PERFORMANCE);
        assertTrue(c.entityRaycastCulling, "profiles re-enable lossless culling");
        assertEquals(32, c.blockEntityDistanceLimit);
        assertEquals(24, c.particleDistanceLimit);
        c.applyProfile(PerformanceProfile.DEFAULT);
        assertEquals(0, c.blockEntityDistanceLimit);
    }
}
