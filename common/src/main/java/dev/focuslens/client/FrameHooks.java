package dev.focuslens.client;

import dev.focuslens.bench.BenchmarkRunner;
import dev.focuslens.bench.FrameTimeRecorder;
import dev.focuslens.culling.CullingManager;
import net.minecraft.client.Minecraft;

/** Work done at the start of every rendered frame (hooked at Minecraft#renderFrame). */
public final class FrameHooks {
    private FrameHooks() {
    }

    public static void onFrameStart(Minecraft minecraft) {
        FrameTimeRecorder.onFrameStart();
        CullingManager.beginFrame(minecraft);
        BenchmarkRunner.onFrameStart(minecraft);
    }
}
