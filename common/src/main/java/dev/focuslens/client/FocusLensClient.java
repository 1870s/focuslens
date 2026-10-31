package dev.focuslens.client;

import dev.focuslens.FocusLens;
import dev.focuslens.bench.BenchmarkRunner;
import dev.focuslens.culling.CullingManager;
import dev.focuslens.gui.FocusLensScreen;
import dev.focuslens.mc.McCompat;
import dev.focuslens.zoom.ZoomController;
import net.minecraft.client.Minecraft;

/** Client-side glue shared by both loaders: per-tick key handling. */
public final class FocusLensClient {
    /**
     * Optional unattended benchmark for development/CI machines with a GPU:
     * {@code -Dfocuslens.autoBenchmark=compare|current} starts a benchmark 15 s after joining a world,
     * {@code -Dfocuslens.quitAfterBenchmark=true} closes the game when it is done.
     */
    private static final String AUTO_BENCHMARK = System.getProperty("focuslens.autoBenchmark");
    private static final boolean QUIT_AFTER_BENCHMARK = Boolean.getBoolean("focuslens.quitAfterBenchmark");
    private static int ticksInWorld;
    private static boolean autoBenchmarkStarted;
    private static boolean quitRequested;

    private FocusLensClient() {
    }

    /** Called by the loader module after {@link FocusLens#init()}. */
    public static void init() {
        CullingManager.init();
    }

    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            ZoomController.reset();
            ticksInWorld = 0;
            return;
        }
        ticksInWorld++;
        if (KeyBindings.OPEN_SETTINGS != null) {
            while (KeyBindings.OPEN_SETTINGS.consumeClick()) {
                if (McCompat.currentScreen(minecraft) == null) {
                    McCompat.setScreen(minecraft, new FocusLensScreen(null));
                }
            }
        }
        if (KeyBindings.RUN_BENCHMARK != null) {
            while (KeyBindings.RUN_BENCHMARK.consumeClick()) {
                BenchmarkRunner.toggle(minecraft, BenchmarkRunner.Mode.COMPARE);
            }
        }
        handleAutoBenchmark(minecraft);
        DocScreenshots.tick(minecraft, ticksInWorld);
        if (MIXIN_AUDIT && ticksInWorld == 100) {
            runMixinAudit(minecraft);
        }
    }

    /**
     * {@code -Dfocuslens.mixinAudit=true}: force-loads every mixin target class (some, like the particle
     * group, are otherwise only loaded once a particle appears), logs the result and closes the game.
     */
    private static final boolean MIXIN_AUDIT = Boolean.getBoolean("focuslens.mixinAudit");

    private static void runMixinAudit(Minecraft minecraft) {
        try {
            org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
            FocusLens.LOG.info("Mixin audit finished without exceptions");
        } catch (Throwable t) {
            FocusLens.LOG.error("Mixin audit failed", t);
        }
        // Loader-independent check: loading each target class applies our mixins; their handler methods
        // (named focuslens$...) must then be present in the class.
        String[] targets = {
                "net.minecraft.client.Camera", "net.minecraft.client.MouseHandler", "net.minecraft.client.Minecraft",
                "net.minecraft.client.renderer.entity.EntityRenderDispatcher",
                "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher",
                "net.minecraft.client.particle.QuadParticleGroup",
                "net.minecraft.world.entity.Entity", "net.minecraft.world.level.block.entity.BlockEntity"};
        int failures = 0;
        for (String name : targets) {
            try {
                Class<?> type = Class.forName(name, true, FocusLensClient.class.getClassLoader());
                long handlers = java.util.Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().contains("focuslens$")).count();
                if (handlers == 0) {
                    failures++;
                    FocusLens.LOG.error("Mixin check FAILED: no FocusLens handler in {}", name);
                } else {
                    FocusLens.LOG.info("Mixin check OK: {} ({} FocusLens methods)", name, handlers);
                }
            } catch (Throwable t) {
                failures++;
                FocusLens.LOG.error("Mixin check FAILED: could not load {}", name, t);
            }
        }
        FocusLens.LOG.info("Mixin check result: {} of {} target classes OK", targets.length - failures, targets.length);
        minecraft.stop();
    }

    private static void handleAutoBenchmark(Minecraft minecraft) {
        if (AUTO_BENCHMARK == null) {
            return;
        }
        if (!autoBenchmarkStarted && ticksInWorld > 20 * 15 && McCompat.currentScreen(minecraft) == null) {
            autoBenchmarkStarted = true;
            BenchmarkRunner.Mode mode = "current".equalsIgnoreCase(AUTO_BENCHMARK) ? BenchmarkRunner.Mode.CURRENT : BenchmarkRunner.Mode.COMPARE;
            FocusLens.LOG.info("Starting automatic benchmark ({})", mode);
            BenchmarkRunner.start(minecraft, mode);
        } else if (autoBenchmarkStarted && QUIT_AFTER_BENCHMARK && !quitRequested && !BenchmarkRunner.isRunning()) {
            quitRequested = true;
            FocusLens.LOG.info("Automatic benchmark done, closing the game");
            minecraft.stop();
        }
    }
}
