package dev.focuslens.bench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.focuslens.FocusLens;
import dev.focuslens.compat.ModCompat;
import dev.focuslens.culling.CullingManager;
import dev.focuslens.culling.CullingStats;
import dev.focuslens.mc.McCompat;
import dev.focuslens.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reproducible in-game benchmark.
 *
 * <p>The camera is driven along a fixed path (one full turn at constant speed, pitch locked) so every
 * pass sees the same scene in the same order regardless of frame rate. Mouse look is ignored while it
 * runs. "Compare" mode alternates passes with all FocusLens optimizations suspended (OFF) and active
 * (ON) — OFF, ON, OFF, ON — so slow drift (thermal throttling, chunk loading, background tasks) affects
 * both sides equally. Each pass is preceded by an unrecorded settle period.
 *
 * <p>Results are written to {@code <game dir>/focuslens/benchmarks/} as JSON (summary, environment,
 * settings) and CSV (every frame time), and summarized in chat.
 */
public final class BenchmarkRunner {
    public enum Mode { CURRENT, COMPARE }

    private static final double SETTLE_SECONDS = 4.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Mode mode;
    private static List<Pass> passes;
    private static int passIndex;
    private static boolean settling;
    private static long phaseStartNanos;
    private static float startYaw;
    private static float lockedPitch;
    private static long[] statSums;
    private static int statFrames;

    private BenchmarkRunner() {
    }

    public static boolean isRunning() {
        return passes != null;
    }

    /** True while the benchmark owns the camera (mouse look is ignored). */
    public static boolean isControllingCamera() {
        return passes != null;
    }

    public static void toggle(Minecraft minecraft, Mode requested) {
        if (isRunning()) {
            abort(minecraft, "Benchmark cancelled.");
        } else {
            start(minecraft, requested);
        }
    }

    public static void start(Minecraft minecraft, Mode requested) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        int seconds = FocusLens.config().benchmarkSeconds;
        mode = requested;
        passes = new ArrayList<>();
        if (requested == Mode.COMPARE) {
            passes.add(new Pass("OFF", true, seconds));
            passes.add(new Pass("ON", false, seconds));
            passes.add(new Pass("OFF", true, seconds));
            passes.add(new Pass("ON", false, seconds));
        } else {
            passes.add(new Pass("CURRENT", CullingManager.isSuspended(), seconds));
        }
        passIndex = 0;
        startYaw = player.getYRot();
        lockedPitch = Math.max(-30.0F, Math.min(30.0F, player.getXRot()));
        McCompat.setScreen(minecraft, null);
        beginSettle();
        int total = (int) Math.round(passes.size() * (seconds + SETTLE_SECONDS));
        message(minecraft, Component.literal("FocusLens benchmark started (" + passes.size() + " pass(es), about " + total
                + " s). Do not move. Press the benchmark key again to cancel.").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (minecraft.options.enableVsync().get() || minecraft.options.framerateLimit().get() < 260) {
            message(minecraft, Component.literal("Warning: VSync or an FPS limit is active; results will be capped.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void beginSettle() {
        Pass pass = passes.get(passIndex);
        CullingManager.setSuspended(pass.suspended);
        settling = true;
        phaseStartNanos = System.nanoTime();
    }

    /** Main thread, start of every frame. */
    public static void onFrameStart(Minecraft minecraft) {
        if (passes == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            abort(minecraft, null);
            return;
        }
        if (McCompat.currentScreen(minecraft) != null) {
            abort(minecraft, "Benchmark cancelled because a screen was opened.");
            return;
        }
        // Vanilla throttles to 30 FPS after 60 s without input ("AFK"); the benchmark counts as input.
        minecraft.getFramerateLimitTracker().onInputReceived();
        Pass pass = passes.get(passIndex);
        double elapsed = (System.nanoTime() - phaseStartNanos) / 1.0e9;

        if (settling) {
            setRotation(player, startYaw, lockedPitch);
            if (elapsed >= SETTLE_SECONDS) {
                settling = false;
                phaseStartNanos = System.nanoTime();
                statSums = new long[CullingStats.COUNT];
                statFrames = 0;
                FrameTimeRecorder.startRecording();
            }
            return;
        }

        double progress = Math.min(1.0, elapsed / pass.seconds);
        setRotation(player, (float) (startYaw + 360.0 * progress), lockedPitch);
        for (int i = 0; i < CullingStats.COUNT; i++) {
            statSums[i] += CullingStats.last(i);
        }
        statFrames++;

        if (elapsed >= pass.seconds) {
            double[] frameTimes = FrameTimeRecorder.stopRecording();
            pass.frameTimesMs = frameTimes;
            pass.stats = FrameStats.of(frameTimes, frameTimes.length);
            pass.averageCulled = new double[CullingStats.COUNT];
            for (int i = 0; i < CullingStats.COUNT; i++) {
                pass.averageCulled[i] = statFrames > 0 ? (double) statSums[i] / statFrames : 0.0;
            }
            passIndex++;
            if (passIndex >= passes.size()) {
                finish(minecraft);
            } else {
                beginSettle();
            }
        }
    }

    private static void setRotation(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    private static void abort(Minecraft minecraft, String reason) {
        FrameTimeRecorder.stopRecording();
        CullingManager.setSuspended(false);
        passes = null;
        if (reason != null) {
            message(minecraft, Component.literal(reason).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void finish(Minecraft minecraft) {
        List<Pass> done = passes;
        Mode doneMode = mode;
        passes = null;
        CullingManager.setSuspended(false);
        try {
            Path file = write(minecraft, doneMode, done);
            summarize(minecraft, doneMode, done, file);
        } catch (IOException e) {
            FocusLens.LOG.error("Could not write benchmark results", e);
            message(minecraft, Component.literal("Benchmark finished, but the results could not be saved: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private static void summarize(Minecraft minecraft, Mode doneMode, List<Pass> done, Path file) {
        message(minecraft, Component.literal("FocusLens benchmark finished:").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Pass pass : done) {
            FrameStats s = pass.stats;
            message(minecraft, Component.literal(String.format(Locale.ROOT, "  %-7s avg %.1f FPS | 1%% low %.1f | P99 %.2f ms | culled/frame E %.0f BE %.0f P %.0f",
                    pass.label, s.averageFps(), s.onePercentLowFps(), s.p99FrameTimeMs(),
                    sum(pass.averageCulled, CullingStats.ENTITY_SECTION, CullingStats.ENTITY_DISTANCE),
                    sum(pass.averageCulled, CullingStats.BLOCK_ENTITY_FRUSTUM, CullingStats.BLOCK_ENTITY_DISTANCE),
                    sum(pass.averageCulled, CullingStats.PARTICLE_OCCLUSION, CullingStats.PARTICLE_DISTANCE))));
        }
        if (doneMode == Mode.COMPARE) {
            double off = meanFps(done, true);
            double on = meanFps(done, false);
            double offLow = meanLow(done, true);
            double onLow = meanLow(done, false);
            message(minecraft, Component.literal(String.format(Locale.ROOT, "  Result: average FPS %+.1f%% (%.1f -> %.1f), 1%% low %+.1f%% (%.1f -> %.1f)",
                    percentChange(off, on), off, on, percentChange(offLow, onLow), offLow, onLow)).withStyle(ChatFormatting.WHITE));
        }
        message(minecraft, Component.literal("  Saved to " + file.toAbsolutePath()).withStyle(ChatFormatting.GRAY));
    }

    private static double sum(double[] values, int from, int toInclusive) {
        double s = 0;
        for (int i = from; i <= toInclusive; i++) s += values[i];
        return s;
    }

    private static double meanFps(List<Pass> done, boolean suspended) {
        return done.stream().filter(p -> p.suspended == suspended).mapToDouble(p -> p.stats.averageFps()).average().orElse(0);
    }

    private static double meanLow(List<Pass> done, boolean suspended) {
        return done.stream().filter(p -> p.suspended == suspended).mapToDouble(p -> p.stats.onePercentLowFps()).average().orElse(0);
    }

    static double percentChange(double before, double after) {
        return before > 0 ? (after - before) / before * 100.0 : 0.0;
    }

    private static Path write(Minecraft minecraft, Mode doneMode, List<Pass> done) throws IOException {
        Path dir = Services.PLATFORM.gameDir().resolve("focuslens").resolve("benchmarks");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path json = dir.resolve("benchmark-" + stamp + ".json");
        Path csv = dir.resolve("benchmark-" + stamp + "-frametimes.csv");

        JsonObject root = new JsonObject();
        root.addProperty("mode", doneMode.name());
        root.add("environment", environment(minecraft));
        root.add("settings", GSON.toJsonTree(FocusLens.config()));
        JsonArray passArray = new JsonArray();
        for (Pass pass : done) {
            JsonObject p = new JsonObject();
            p.addProperty("label", pass.label);
            p.addProperty("focusLensOptimizationsActive", !pass.suspended);
            p.addProperty("seconds", pass.seconds);
            p.add("stats", GSON.toJsonTree(pass.stats));
            JsonObject culled = new JsonObject();
            String[] names = {"entitySection", "entityRaycast", "entityDistance", "blockEntityFrustum", "blockEntityRaycast",
                    "blockEntityDistance", "particleOcclusion", "particleDistance"};
            for (int i = 0; i < names.length; i++) {
                culled.addProperty(names[i], pass.averageCulled[i]);
            }
            p.add("averageCulledPerFrame", culled);
            passArray.add(p);
        }
        root.add("passes", passArray);
        try (Writer writer = Files.newBufferedWriter(json, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try (Writer writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            writer.write("pass,label,frame,frame_time_ms\n");
            for (int i = 0; i < done.size(); i++) {
                Pass pass = done.get(i);
                for (int f = 0; f < pass.frameTimesMs.length; f++) {
                    writer.write(i + "," + pass.label + "," + f + "," + String.format(Locale.ROOT, "%.4f", pass.frameTimesMs[f]) + "\n");
                }
            }
        }
        FocusLens.LOG.info("Benchmark results written to {}", json);
        return json;
    }

    private static JsonObject environment(Minecraft minecraft) {
        JsonObject env = new JsonObject();
        env.addProperty("minecraft", Services.PLATFORM.minecraftVersion());
        env.addProperty("loader", Services.PLATFORM.loaderName());
        env.addProperty("java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        env.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        env.addProperty("cpu", cpuName());
        env.addProperty("logicalCores", Runtime.getRuntime().availableProcessors());
        env.addProperty("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        env.addProperty("gpu", McCompat.gpuDescription());
        env.addProperty("windowWidth", minecraft.getWindow().getWidth());
        env.addProperty("windowHeight", minecraft.getWindow().getHeight());
        env.addProperty("renderDistance", minecraft.options.getEffectiveRenderDistance());
        env.addProperty("simulationDistance", minecraft.options.simulationDistance().get());
        env.addProperty("vsync", minecraft.options.enableVsync().get());
        env.addProperty("framerateLimit", minecraft.options.framerateLimit().get());
        env.addProperty("singleplayer", minecraft.hasSingleplayerServer());
        if (minecraft.level != null) {
            env.addProperty("dimension", minecraft.level.dimension().identifier().toString());
            env.addProperty("entitiesInLevel", minecraft.level.getEntityCount());
        }
        if (minecraft.player != null) {
            env.addProperty("position", String.format(Locale.ROOT, "%.1f %.1f %.1f", minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()));
        }
        JsonArray mods = new JsonArray();
        for (String id : List.of("sodium", "iris", "lithium", "entityculling", "immediatelyfast", "ferritecore", "moreculling")) {
            if (ModCompat.isLoaded(id)) mods.add(id);
        }
        env.add("relevantModsLoaded", mods);
        return env;
    }

    private static String cpuName() {
        try {
            return new oshi.SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName().trim();
        } catch (Throwable t) {
            String id = System.getenv("PROCESSOR_IDENTIFIER");
            return id != null ? id : "unknown";
        }
    }

    private static void message(Minecraft minecraft, Component text) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(text);
        }
        FocusLens.LOG.info(text.getString());
    }

    /** Progress text for the overlay, or null when idle. */
    public static String statusLine() {
        if (passes == null) {
            return null;
        }
        Pass pass = passes.get(passIndex);
        double elapsed = (System.nanoTime() - phaseStartNanos) / 1.0e9;
        if (settling) {
            return String.format(Locale.ROOT, "Benchmark pass %d/%d (%s): settling %.0f s", passIndex + 1, passes.size(), pass.label, Math.max(0, SETTLE_SECONDS - elapsed));
        }
        return String.format(Locale.ROOT, "Benchmark pass %d/%d (%s): %.0f / %d s", passIndex + 1, passes.size(), pass.label, elapsed, pass.seconds);
    }

    private static final class Pass {
        final String label;
        final boolean suspended;
        final int seconds;
        double[] frameTimesMs = new double[0];
        FrameStats stats;
        double[] averageCulled = new double[CullingStats.COUNT];

        Pass(String label, boolean suspended, int seconds) {
            this.label = label;
            this.suspended = suspended;
            this.seconds = seconds;
        }
    }
}
