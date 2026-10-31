package dev.focuslens.client;

import dev.focuslens.FocusLens;
import dev.focuslens.culling.CullingManager;
import dev.focuslens.gui.FocusLensScreen;
import dev.focuslens.mc.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development aid, inactive unless {@code -Dfocuslens.docScreenshots=true} is set. After joining a world
 * it saves real in-game screenshots to {@code screenshots/}:
 * <ul>
 *     <li>each settings section and the performance overlay (for the README);</li>
 *     <li>the same four views (north/east/south/west) with all optimizations suspended and active, so
 *     {@code tools/bench/compare_screenshots.py} can prove that the lossless culling hides nothing
 *     visible.</li>
 * </ul>
 * Then it closes the game.
 */
final class DocScreenshots {
    static final boolean ENABLED = Boolean.getBoolean("focuslens.docScreenshots");
    private static final int TICKS_PER_STEP = 60;

    private record Step(String screenshot, Consumer<Minecraft> setup) {
    }

    private static List<Step> steps;
    private static int index = -1;
    private static int ticks;
    private static float yaw;

    private DocScreenshots() {
    }

    private static List<Step> createSteps() {
        List<Step> list = new ArrayList<>();
        list.add(new Step("settings-zoom", mc -> McCompat.setScreen(mc, FocusLensScreen.onTab(null, 0))));
        list.add(new Step("settings-performance", mc -> McCompat.setScreen(mc, FocusLensScreen.onTab(null, 1))));
        list.add(new Step("settings-general", mc -> McCompat.setScreen(mc, FocusLensScreen.onTab(null, 2))));
        list.add(new Step("overlay", mc -> {
            McCompat.setScreen(mc, null);
            FocusLens.config().showPerformanceOverlay = true;
        }));
        // Zoom check: the same view without and with the zoom key held (pressed programmatically).
        list.add(new Step("zoom-normal", mc -> FocusLens.config().showPerformanceOverlay = false));
        list.add(new Step("zoom-active", mc -> KeyBindings.ZOOM.setDown(true)));
        String[] directions = {"south", "west", "north", "east"};
        for (int i = 0; i < directions.length; i++) {
            float viewYaw = i * 90.0F;
            list.add(new Step("view-" + directions[i] + "-off", mc -> {
                KeyBindings.ZOOM.setDown(false);
                FocusLens.config().showPerformanceOverlay = false;
                yaw = viewYaw;
                CullingManager.setSuspended(true);
            }));
            list.add(new Step("view-" + directions[i] + "-on", mc -> CullingManager.setSuspended(false)));
        }
        return list;
    }

    static void tick(Minecraft minecraft, int ticksInWorld) {
        if (!ENABLED || ticksInWorld < 20 * 15) {
            return;
        }
        if (steps == null) {
            steps = createSteps();
            index = 0;
            ticks = 0;
            steps.get(0).setup.accept(minecraft);
            return;
        }
        if (index >= 4) {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                player.setYRot(yaw);
                player.yRotO = yaw;
                player.setXRot(10.0F);
                player.xRotO = 10.0F;
            }
        }
        if (++ticks < TICKS_PER_STEP) {
            return;
        }
        ticks = 0;
        if (index < steps.size()) {
            logZoomState(minecraft, steps.get(index).screenshot);
            String name = "focuslens-" + steps.get(index).screenshot + ".png";
            Screenshot.grab(minecraft.gameDirectory, name, McCompat.mainRenderTarget(minecraft), 1,
                    message -> FocusLens.LOG.info("Saved documentation screenshot {}", name));
            index++;
            if (index < steps.size()) {
                steps.get(index).setup.accept(minecraft);
            }
        } else {
            CullingManager.setSuspended(false);
            FocusLens.config().showPerformanceOverlay = false;
            logZoomState(minecraft, "after-release");
            minecraft.stop();
        }
    }

    /** Logs what the zoom actually did, so a run can be checked without looking at the images. */
    private static void logZoomState(Minecraft minecraft, String label) {
        FocusLens.LOG.info("Zoom check [{}]: camera FOV {} | FOV option {} | zoom active {} | cinematic option {} | cinematic forced {}",
                label, String.format(java.util.Locale.ROOT, "%.2f", McCompat.mainCamera(minecraft).getFov()),
                minecraft.options.fov().get(), dev.focuslens.zoom.ZoomController.isActive(),
                minecraft.options.smoothCamera, dev.focuslens.zoom.ZoomController.forceCinematicCamera());
    }
}
