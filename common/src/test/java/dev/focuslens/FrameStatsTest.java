package dev.focuslens;

import dev.focuslens.bench.FrameStats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameStatsTest {
    @Test
    void constantFrameTimes() {
        double[] t = new double[1000];
        Arrays.fill(t, 10.0);
        FrameStats s = FrameStats.of(t, t.length);
        assertEquals(1000, s.frames());
        assertEquals(10.0, s.totalSeconds(), 1e-9);
        assertEquals(100.0, s.averageFps(), 1e-9);
        assertEquals(100.0, s.onePercentLowFps(), 1e-9);
        assertEquals(10.0, s.p99FrameTimeMs(), 1e-9);
        assertEquals(0.0, s.stdDevFrameTimeMs(), 1e-9);
    }

    @Test
    void onePercentLowUsesSlowestFrames() {
        double[] t = new double[1000];
        Arrays.fill(t, 5.0);
        for (int i = 0; i < 10; i++) t[i * 100] = 50.0; // exactly 1% of frames are slow
        FrameStats s = FrameStats.of(t, t.length);
        assertEquals(20.0, s.onePercentLowFps(), 1e-9);  // 1000 / 50
        assertEquals(20.0, s.pointOnePercentLowFps(), 1e-9);
        assertEquals(50.0, s.maxFrameTimeMs(), 1e-9);
        // Average FPS = frames / time, not the mean of per-frame FPS.
        assertEquals(1000 * 1000.0 / (990 * 5.0 + 10 * 50.0), s.averageFps(), 1e-9);
    }

    @Test
    void respectsCountArgument() {
        double[] t = {10.0, 10.0, 999.0};
        FrameStats s = FrameStats.of(t, 2);
        assertEquals(2, s.frames());
        assertEquals(100.0, s.averageFps(), 1e-9);
    }

    @Test
    void emptyInput() {
        FrameStats s = FrameStats.of(new double[0], 0);
        assertEquals(0, s.frames());
        assertEquals(0.0, s.averageFps(), 0.0);
    }
}
