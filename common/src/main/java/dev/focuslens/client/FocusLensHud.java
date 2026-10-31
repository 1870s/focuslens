package dev.focuslens.client;

import dev.focuslens.FocusLens;
import dev.focuslens.bench.BenchmarkRunner;
import dev.focuslens.bench.FrameTimeRecorder;
import dev.focuslens.culling.CullingManager;
import dev.focuslens.culling.CullingStats;
import dev.focuslens.gui.Theme;
import dev.focuslens.mc.McCompat;
import dev.focuslens.zoom.ZoomController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/** HUD layer: zoom level while scrolling, optional performance overlay, benchmark progress. */
public final class FocusLensHud {
    private FocusLensHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McCompat.isHudHidden(minecraft)) {
            return;
        }
        Font font = minecraft.font;

        if (FocusLens.config().showZoomLevel && ZoomController.recentlyScrolled()) {
            String text = String.format(Locale.ROOT, "%.1fx", ZoomController.currentFactor());
            graphics.centeredText(font, text, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 12, Theme.ACCENT_LIGHT);
        }

        String benchmark = BenchmarkRunner.statusLine();
        boolean overlay = FocusLens.config().showPerformanceOverlay;
        if (!overlay && benchmark == null) {
            return;
        }
        int x = 4;
        int y = 4;
        if (overlay) {
            double ms = FrameTimeRecorder.rollingAverageMs();
            String[] lines = {
                    String.format(Locale.ROOT, "FocusLens  %d FPS  %.2f ms (max %.1f)", minecraft.getFps(), ms, FrameTimeRecorder.rollingMaxMs()),
                    String.format(Locale.ROOT, "Hidden entities %d  (section %d, ray %d, distance %d)", CullingStats.lastEntities(),
                            CullingStats.last(CullingStats.ENTITY_SECTION), CullingStats.last(CullingStats.ENTITY_RAYCAST), CullingStats.last(CullingStats.ENTITY_DISTANCE)),
                    String.format(Locale.ROOT, "Hidden block entities %d  (frustum %d, ray %d, distance %d)", CullingStats.lastBlockEntities(),
                            CullingStats.last(CullingStats.BLOCK_ENTITY_FRUSTUM), CullingStats.last(CullingStats.BLOCK_ENTITY_RAYCAST), CullingStats.last(CullingStats.BLOCK_ENTITY_DISTANCE)),
                    String.format(Locale.ROOT, "Hidden particles %d  (occlusion %d, distance %d)%s", CullingStats.lastParticles(),
                            CullingStats.last(CullingStats.PARTICLE_OCCLUSION), CullingStats.last(CullingStats.PARTICLE_DISTANCE),
                            CullingManager.isSuspended() ? "  [optimizations OFF]" : "")
            };
            int width = 0;
            for (String line : lines) width = Math.max(width, font.width(line));
            graphics.fill(x - 2, y - 2, x + width + 2, y + lines.length * 10, Theme.OVERLAY_BACKGROUND);
            for (String line : lines) {
                graphics.text(font, line, x, y, Theme.TEXT, false);
                y += 10;
            }
            y += 4;
        }
        if (benchmark != null) {
            graphics.fill(x - 2, y - 2, x + font.width(benchmark) + 2, y + 10, Theme.OVERLAY_BACKGROUND);
            graphics.text(font, benchmark, x, y, Theme.ACCENT_LIGHT, false);
        }
    }
}
