package dev.focuslens.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.focuslens.FocusLens;
import dev.focuslens.bench.BenchmarkRunner;
import dev.focuslens.client.KeyBindings;
import dev.focuslens.compat.ModCompat;
import dev.focuslens.config.ConfigManager;
import dev.focuslens.config.FocusLensConfig;
import dev.focuslens.config.PerformanceProfile;
import dev.focuslens.config.ZoomActivation;
import dev.focuslens.config.ZoomSensitivityMode;
import dev.focuslens.culling.CullingManager;
import dev.focuslens.mc.McCompat;
import dev.focuslens.platform.Services;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The settings screen: a sidebar with three sections (Zoom, Performance, General), a scrollable list
 * of settings and a description panel that explains the hovered setting.
 */
public class FocusLensScreen extends Screen {
    private enum Tab {
        ZOOM("Zoom"), PERFORMANCE("Performance"), GENERAL("General");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final int HEADER_HEIGHT = 34;
    private static final int SIDEBAR_WIDTH = 112;
    private static final int FOOTER_HEIGHT = 62;
    private static final int MAX_CONTENT_WIDTH = 420;
    private static Tab lastTab = Tab.ZOOM;

    private final @Nullable Screen parent;
    private Tab tab = lastTab;
    private final List<SettingRow> rows = new ArrayList<>();
    private int contentX, contentY, contentWidth, contentHeight;
    private int scroll;
    private int totalRowHeight;
    private SettingRow dragging;
    private SettingRow.KeyBind listening;
    private SettingRow hovered;

    public FocusLensScreen(@Nullable Screen parent) {
        super(Component.literal("FocusLens"));
        this.parent = parent;
    }

    /** Opens the screen on a given section: 0 = Zoom, 1 = Performance, 2 = General. */
    public static FocusLensScreen onTab(@Nullable Screen parent, int tabIndex) {
        FocusLensScreen screen = new FocusLensScreen(parent);
        screen.tab = Tab.values()[Math.floorMod(tabIndex, Tab.values().length)];
        lastTab = screen.tab;
        return screen;
    }

    private static FocusLensConfig config() {
        return FocusLens.config();
    }

    @Override
    protected void init() {
        contentX = SIDEBAR_WIDTH + 12;
        contentY = HEADER_HEIGHT + 6;
        contentWidth = Math.min(MAX_CONTENT_WIDTH, this.width - contentX - 12);
        contentHeight = this.height - contentY - FOOTER_HEIGHT - 6;
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        listening = null;
        dragging = null;
        switch (tab) {
            case ZOOM -> buildZoomRows();
            case PERFORMANCE -> buildPerformanceRows();
            case GENERAL -> buildGeneralRows();
        }
        totalRowHeight = 0;
        for (SettingRow row : rows) totalRowHeight += row.height();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    // ------------------------------------------------------------------ tabs

    private void buildZoomRows() {
        rows.add(new SettingRow.Heading("OptiFine zoom"));
        rows.add(new SettingRow.Toggle("Enable zoom",
                "Turns the zoom on or off completely. When off, the zoom key does nothing.",
                () -> config().zoomEnabled, v -> config().zoomEnabled = v));
        rows.add(new SettingRow.KeyBind("Zoom key",
                "Key that activates the zoom. OptiFine uses C. Click, then press a key; Escape unbinds it. "
                        + (ModCompat.otherZoomMod().map(id -> "Another zoom mod (" + id + ") is installed, so this starts unbound.").orElse("")),
                () -> KeyBindings.ZOOM));
        rows.add(new SettingRow.Cycle<>("Activation",
                "Hold: zoom only while the key is held (OptiFine). Toggle: press once to zoom in, again to zoom out.",
                ZoomActivation.values(), () -> config().zoomActivation, v -> config().zoomActivation = v, v -> v.label));
        rows.add(new SettingRow.Slider("Zoom factor",
                "How many times the field of view is narrowed. OptiFine uses 4x, i.e. the final FOV is divided by 4.",
                FocusLensConfig.MIN_ZOOM_FACTOR, 16.0, 0.5, () -> config().zoomFactor, v -> config().zoomFactor = v,
                v -> String.format(Locale.ROOT, v == 4.0 ? "%.1fx (OptiFine)" : "%.1fx", v)));
        rows.add(new SettingRow.Cycle<>("Mouse while zoomed",
                "Cinematic: smoothed camera like OptiFine (your own setting is untouched). "
                        + "Scaled to FOV: precise aim, the picture moves at the same on-screen speed as unzoomed. Unchanged: vanilla.",
                ZoomSensitivityMode.values(), () -> config().zoomSensitivityMode, v -> config().zoomSensitivityMode = v, v -> v.label));
        rows.add(new SettingRow.Slider("Zoom sensitivity",
                "Extra multiplier on mouse movement while zoomed, on top of the mode above. 100% = no change.",
                0.1, 2.0, 0.05, () -> config().zoomSensitivity, v -> config().zoomSensitivity = v,
                v -> Math.round(v * 100) + "%"));
        rows.add(new SettingRow.Toggle("Zoom the hand",
                "Applies the zoom to the first-person hand and held item as well, like OptiFine. Turn off to keep the hand at its normal size.",
                () -> config().zoomFirstPersonHand, v -> config().zoomFirstPersonHand = v));

        rows.add(new SettingRow.Heading("Extras (not in OptiFine)"));
        rows.add(new SettingRow.Toggle("Scroll to adjust zoom",
                "While zooming, the mouse wheel changes the zoom level (1.2x per step, up to 50x) instead of the hotbar slot.",
                () -> config().scrollZoom, v -> config().scrollZoom = v));
        rows.add(new SettingRow.Toggle("Reset scroll zoom on release",
                "On: every zoom starts at the zoom factor above. Off: the last scrolled zoom level is kept until the game restarts.",
                () -> config().resetScrollZoomOnRelease, v -> config().resetScrollZoomOnRelease = v)
                .disabledWhen(() -> config().scrollZoom ? null : "Needs scroll zoom"));
        rows.add(new SettingRow.Toggle("Show zoom level",
                "Briefly shows the current zoom level under the crosshair after scrolling.",
                () -> config().showZoomLevel, v -> config().showZoomLevel = v)
                .disabledWhen(() -> config().scrollZoom ? null : "Needs scroll zoom"));
        rows.add(new SettingRow.Toggle("Smooth zoom animation",
                "Eases the field of view in and out instead of switching instantly. OptiFine switches instantly.",
                () -> config().zoomAnimation, v -> config().zoomAnimation = v));
        rows.add(new SettingRow.Slider("Animation speed",
                "How fast the animated zoom reaches its target. Higher is faster.",
                2.0, 40.0, 1.0, () -> config().zoomAnimationSpeed, v -> config().zoomAnimationSpeed = v,
                v -> String.format(Locale.ROOT, "%.0f", v))
                .disabledWhen(() -> config().zoomAnimation ? null : "Needs zoom animation"));
        rows.add(new SettingRow.Action("Restore OptiFine behaviour",
                "Resets every zoom setting to the defaults that reproduce OptiFine: hold C, 4x, cinematic camera, no animation, no scroll zoom.",
                "Restore", this::restoreZoomDefaults));
    }

    private void buildPerformanceRows() {
        rows.add(new SettingRow.Cycle<>("Profile",
                "Default: only optimizations that never change what you see. Balanced/Performance also hide far block entities, "
                        + "decorative entities and particles. Custom: a setting was changed by hand.",
                new PerformanceProfile[]{PerformanceProfile.DEFAULT, PerformanceProfile.BALANCED, PerformanceProfile.PERFORMANCE},
                () -> config().performanceProfile, v -> config().applyProfile(v), v -> v.label));

        rows.add(new SettingRow.Heading("Lossless culling"));
        rows.add((new SettingRow.Toggle("Entity section culling",
                "Skips entities in 16x16x16 sections that vanilla's cave-culling graph proved invisible. "
                        + "Vanilla uses this graph for terrain and block entities, not for entities.",
                () -> config().entitySectionCulling, v -> config().entitySectionCulling = v))
                .disabledWhen(() -> CullingManager.sodiumPresent() ? "Sodium handles this" : null));
        rows.add((new SettingRow.Toggle("Entity ray culling",
                "A background thread casts rays to each entity; entities fully behind solid blocks are not drawn. "
                        + "Results are one frame old, so a fast turn can reveal an entity one frame late.",
                () -> config().entityRaycastCulling, v -> config().entityRaycastCulling = v))
                .disabledWhen(() -> CullingManager.entityCullingModPresent() ? "Entity Culling mod does this" : null));
        rows.add((new SettingRow.Toggle("Block entity ray culling",
                "Same ray test for chests, signs, banners, skulls, shulker boxes and other block entities with their own renderer.",
                () -> config().blockEntityRaycastCulling, v -> config().blockEntityRaycastCulling = v))
                .disabledWhen(() -> CullingManager.entityCullingModPresent() ? "Entity Culling mod does this" : null));
        rows.add((new SettingRow.Toggle("Block entity frustum culling",
                "Tests each block entity against the view frustum. Vanilla only tests whole 16x16x16 sections.",
                () -> config().blockEntityFrustumCulling, v -> config().blockEntityFrustumCulling = v))
                .disabledWhen(() -> Services.PLATFORM.loaderCullsBlockEntitiesByFrustum() ? "NeoForge already does this" : null));
        rows.add((new SettingRow.Toggle("Particle occlusion culling",
                "Skips particles inside sections that the cave-culling graph proved invisible (e.g. lava and smoke particles in caves behind terrain).",
                () -> config().particleOcclusionCulling, v -> config().particleOcclusionCulling = v))
                .disabledWhen(() -> CullingManager.sodiumPresent() ? "Not available with Sodium" : null));
        rows.add((new SettingRow.Toggle("Keep name tags visible",
                "Never culls players and entities whose name tag is shown, because vanilla draws name tags through walls.",
                () -> config().keepNameTagsVisible, v -> config().keepNameTagsVisible = v)));

        rows.add(new SettingRow.Heading("Distance limits (visible trade-off)"));
        rows.add((distanceSlider("Block entity distance",
                "Block entities farther than this are not drawn. Vanilla draws most of them up to 64 blocks. Off = vanilla.",
                () -> config().blockEntityDistanceLimit, v -> config().blockEntityDistanceLimit = v)));
        rows.add((distanceSlider("Decorative entity distance",
                "Dropped items, item frames, paintings, armor stands and experience orbs farther than this are not drawn. Off = vanilla.",
                () -> config().decorativeEntityDistanceLimit, v -> config().decorativeEntityDistanceLimit = v)));
        rows.add((distanceSlider("Particle distance",
                "Particles farther than this are not drawn. Off = vanilla.",
                () -> config().particleDistanceLimit, v -> config().particleDistanceLimit = v)));
    }

    private void buildGeneralRows() {
        rows.add(new SettingRow.Heading("Overlay and keys"));
        rows.add(new SettingRow.Toggle("Performance overlay",
                "Shows FPS, average/max frame time and how many entities, block entities and particles FocusLens hid in the last frame.",
                () -> config().showPerformanceOverlay, v -> config().showPerformanceOverlay = v));
        rows.add(new SettingRow.KeyBind("Open settings key", "Optional key that opens this screen in game.", () -> KeyBindings.OPEN_SETTINGS));
        rows.add(new SettingRow.KeyBind("Benchmark key", "Optional key that starts (or cancels) the OFF vs ON benchmark.", () -> KeyBindings.RUN_BENCHMARK));

        rows.add(new SettingRow.Heading("Benchmark"));
        rows.add(new SettingRow.Slider("Pass length",
                "Duration of one recorded benchmark pass. The camera makes one full turn per pass. Longer passes give more stable numbers.",
                10, 120, 5, () -> config().benchmarkSeconds, v -> config().benchmarkSeconds = (int) v, v -> (int) v + " s"));
        rows.add(new SettingRow.Action("Compare OFF vs ON",
                "Four passes (OFF, ON, OFF, ON) on a fixed camera path. Prints average FPS and 1% lows to chat and saves "
                        + "JSON/CSV to .minecraft/focuslens/benchmarks.",
                "Start", () -> startBenchmark(BenchmarkRunner.Mode.COMPARE))
                .disabledWhen(() -> this.minecraft == null || this.minecraft.level == null ? "Join a world first" : null));
        rows.add(new SettingRow.Action("Measure current settings",
                "Runs one pass with the current settings and saves the results.",
                "Start", () -> startBenchmark(BenchmarkRunner.Mode.CURRENT))
                .disabledWhen(() -> this.minecraft == null || this.minecraft.level == null ? "Join a world first" : null));

        rows.add(new SettingRow.Heading("Compatibility"));
        rows.add(status("Loader", "The mod loader and Minecraft version FocusLens is running on.",
                Services.PLATFORM.loaderName() + " " + Services.PLATFORM.minecraftVersion(), Theme.TEXT_MUTED));
        rows.add(status("Sodium", "With Sodium, its section culling replaces ours; ray culling and the zoom still work.",
                ModCompat.sodium() ? "Detected" : "Not installed", ModCompat.sodium() ? Theme.GOOD : Theme.TEXT_MUTED));
        rows.add(status("Iris / shaders", "During a shader pack's shadow pass culling is paused so shadows of hidden entities stay correct.",
                ModCompat.iris() ? "Detected" : "Not installed", ModCompat.iris() ? Theme.GOOD : Theme.TEXT_MUTED));
        rows.add(status("Entity Culling", "If installed, FocusLens ray culling turns itself off to avoid doing the same work twice.",
                ModCompat.entityCulling() ? "Detected, FocusLens ray culling off" : "Not installed",
                ModCompat.entityCulling() ? Theme.WARNING : Theme.TEXT_MUTED));
        rows.add(status("Other zoom mods", "Another zoom mod usually also uses C. FocusLens then starts with its zoom key unbound.",
                ModCompat.otherZoomMod().map(id -> "Detected: " + id).orElse("None"),
                ModCompat.otherZoomMod().isPresent() ? Theme.WARNING : Theme.TEXT_MUTED));
    }

    private SettingRow distanceSlider(String label, String description, java.util.function.IntSupplier getter, Consumer<Integer> setter) {
        return new SettingRow.Slider(label, description, 0, 128, 8, getter::getAsInt, v -> {
            int value = (int) v;
            setter.accept(value == 0 ? 0 : Math.max(8, value));
        }, v -> v < 8 ? "Off" : (int) v + " blocks");
    }

    private static SettingRow status(String label, String description, String value, int color) {
        return new SettingRow.Info(label, description, () -> value, () -> color);
    }

    private void restoreZoomDefaults() {
        FocusLensConfig defaults = new FocusLensConfig();
        FocusLensConfig c = config();
        c.zoomEnabled = defaults.zoomEnabled;
        c.zoomActivation = defaults.zoomActivation;
        c.zoomFactor = defaults.zoomFactor;
        c.zoomSensitivityMode = defaults.zoomSensitivityMode;
        c.zoomSensitivity = defaults.zoomSensitivity;
        c.scrollZoom = defaults.scrollZoom;
        c.resetScrollZoomOnRelease = defaults.resetScrollZoomOnRelease;
        c.zoomAnimation = defaults.zoomAnimation;
        c.zoomAnimationSpeed = defaults.zoomAnimationSpeed;
        c.zoomFirstPersonHand = defaults.zoomFirstPersonHand;
        c.showZoomLevel = defaults.showZoomLevel;
        if (KeyBindings.ZOOM != null) {
            KeyBindings.ZOOM.setKey(KeyBindings.ZOOM.getDefaultKey());
            net.minecraft.client.KeyMapping.resetMapping();
        }
        ConfigManager.save();
    }

    private void startBenchmark(BenchmarkRunner.Mode mode) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        ConfigManager.save();
        this.minecraft.options.save();
        McCompat.setScreen(this.minecraft, null);
        BenchmarkRunner.start(this.minecraft, mode);
    }

    // ------------------------------------------------------------------ layout helpers

    private int maxScroll() {
        return Math.max(0, totalRowHeight - contentHeight);
    }

    private void layoutRows() {
        int y = contentY - scroll;
        for (SettingRow row : rows) {
            row.setBounds(contentX, y, contentWidth);
            y += row.height();
        }
    }

    private boolean inContent(double mouseX, double mouseY) {
        return mouseX >= contentX && mouseX < contentX + contentWidth && mouseY >= contentY && mouseY < contentY + contentHeight;
    }

    private int tabY(int index) {
        return HEADER_HEIGHT + 10 + index * 24;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, Theme.BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractBackground(graphics, mouseX, mouseY, a);
        Font font = this.font;
        layoutRows();

        // Header
        graphics.fill(0, 0, this.width, HEADER_HEIGHT, Theme.PANEL);
        graphics.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, Theme.BORDER);
        graphics.fill(12, 11, 15, 23, Theme.ACCENT);
        graphics.text(font, "FocusLens", 21, 10, Theme.TEXT, false);
        graphics.text(font, "Zoom & performance", 21, 21, Theme.TEXT_MUTED, false);
        String version = Services.PLATFORM.loaderName() + " · Minecraft " + Services.PLATFORM.minecraftVersion();
        graphics.text(font, version, this.width - 12 - font.width(version), 14, Theme.TEXT_DISABLED, false);

        // Sidebar
        graphics.fill(0, HEADER_HEIGHT, SIDEBAR_WIDTH, this.height, Theme.SIDEBAR);
        graphics.fill(SIDEBAR_WIDTH - 1, HEADER_HEIGHT, SIDEBAR_WIDTH, this.height, Theme.BORDER);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int ty = tabY(i);
            boolean selected = tabs[i] == tab;
            boolean over = mouseX >= 6 && mouseX < SIDEBAR_WIDTH - 6 && mouseY >= ty && mouseY < ty + 20;
            if (selected) {
                graphics.fill(6, ty, SIDEBAR_WIDTH - 6, ty + 20, Theme.ACCENT_DIM);
                graphics.fill(6, ty, 8, ty + 20, Theme.ACCENT);
            } else if (over) {
                graphics.fill(6, ty, SIDEBAR_WIDTH - 6, ty + 20, Theme.ROW_HOVER);
            }
            graphics.text(font, tabs[i].title, 16, ty + 6, selected ? Theme.TEXT : Theme.TEXT_MUTED, false);
        }

        // Content
        graphics.fill(contentX - 2, contentY - 2, contentX + contentWidth + 2, contentY + contentHeight + 2, Theme.PANEL);
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        hovered = null;
        for (SettingRow row : rows) {
            if (row.y + row.height() < contentY || row.y > contentY + contentHeight) continue;
            boolean over = inContent(mouseX, mouseY) && row.contains(mouseX, mouseY) && !(row instanceof SettingRow.Heading);
            if (over) hovered = row;
            row.extract(graphics, font, mouseX, mouseY, over);
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int barX = contentX + contentWidth + 4;
            int barHeight = Math.max(16, contentHeight * contentHeight / totalRowHeight);
            int barY = contentY + (contentHeight - barHeight) * scroll / maxScroll();
            graphics.fill(barX, contentY, barX + 2, contentY + contentHeight, Theme.CONTROL);
            graphics.fill(barX, barY, barX + 2, barY + barHeight, Theme.ACCENT);
        }

        // Footer: description of the hovered setting, Done button
        int footerY = this.height - FOOTER_HEIGHT;
        graphics.fill(SIDEBAR_WIDTH, footerY, this.width, this.height, Theme.PANEL);
        graphics.fill(SIDEBAR_WIDTH, footerY, this.width, footerY + 1, Theme.BORDER);
        SettingRow described = listening != null ? listening : hovered;
        int textWidth = this.width - contentX - 110;
        if (described != null && described.description != null) {
            String reason = described.disabledReason.get();
            graphics.text(font, described.label + (reason != null ? "  (" + reason + ")" : ""), contentX, footerY + 7,
                    reason != null ? Theme.WARNING : Theme.ACCENT_LIGHT, false);
            List<FormattedCharSequence> lines = font.split(Component.literal(described.description), textWidth);
            int maxLines = Math.max(1, (FOOTER_HEIGHT - 22) / 9);
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                graphics.text(font, lines.get(i), contentX, footerY + 19 + i * 9, Theme.TEXT_MUTED, false);
            }
        } else {
            graphics.text(font, "Hover over a setting to see what it does.", contentX, footerY + 7, Theme.TEXT_DISABLED, false);
            graphics.text(font, "Settings are saved to config/focuslens.json when this screen closes.", contentX, footerY + 19, Theme.TEXT_DISABLED, false);
        }
        int doneX = this.width - 92;
        int doneY = this.height - 30;
        boolean overDone = mouseX >= doneX && mouseX < doneX + 80 && mouseY >= doneY && mouseY < doneY + 20;
        SettingRow.drawButton(graphics, font, doneX, doneY, 80, 20, "Done", overDone, true, true);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (listening != null) {
            // A mouse click while waiting for a key binds the mouse button.
            listening.bind(InputConstants.Type.MOUSE.getOrCreate(button));
            listening = null;
            return true;
        }
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int ty = tabY(i);
            if (mouseX >= 6 && mouseX < SIDEBAR_WIDTH - 6 && mouseY >= ty && mouseY < ty + 20) {
                if (tab != tabs[i]) {
                    tab = tabs[i];
                    lastTab = tab;
                    scroll = 0;
                    rebuildRows();
                    playClick();
                }
                return true;
            }
        }
        int doneX = this.width - 92;
        int doneY = this.height - 30;
        if (mouseX >= doneX && mouseX < doneX + 80 && mouseY >= doneY && mouseY < doneY + 20) {
            playClick();
            onClose();
            return true;
        }
        if (inContent(mouseX, mouseY)) {
            layoutRows();
            for (SettingRow row : rows) {
                if (row.contains(mouseX, mouseY) && row.isEnabled()) {
                    if (row.click(mouseX, mouseY, button)) {
                        if (row instanceof SettingRow.KeyBind keyBind) {
                            listening = keyBind;
                        }
                        if (row.isDraggable()) {
                            dragging = row;
                        }
                        onSettingChanged(row);
                        playClick();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(event.x());
            onSettingChanged(dragging);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inContent(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(scrollY * 16)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            listening.bind(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            listening = null;
            if (this.minecraft != null) {
                this.minecraft.options.save();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void onSettingChanged(SettingRow row) {
        if (tab == Tab.PERFORMANCE && !(row instanceof SettingRow.Cycle<?>) && row.isEnabled()) {
            config().performanceProfile = PerformanceProfile.CUSTOM;
        }
        if (!(row instanceof SettingRow.Slider)) {
            ConfigManager.save();
        }
    }

    private void playClick() {
        if (this.minecraft != null) {
            AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
        }
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        if (this.minecraft != null) {
            this.minecraft.options.save();
            McCompat.setScreen(this.minecraft, parent);
        }
    }
}
