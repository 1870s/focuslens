package dev.focuslens.bench;

import java.util.Arrays;

/**
 * Summary statistics of a list of frame times. Pure Java, unit tested.
 *
 * <p>Definitions (they vary between tools, so they are spelled out here and in the README):
 * <ul>
 *     <li><b>Average FPS</b> = frames / total time (not the mean of per-frame FPS values).</li>
 *     <li><b>1% low FPS</b> = 1000 / (mean frame time of the slowest 1% of frames).</li>
 *     <li><b>0.1% low FPS</b> = the same for the slowest 0.1% (at least one frame).</li>
 *     <li><b>P50/P95/P99 frame time</b> = nearest-rank percentiles of the frame time in ms.</li>
 * </ul>
 */
public record FrameStats(
        int frames,
        double totalSeconds,
        double averageFps,
        double onePercentLowFps,
        double pointOnePercentLowFps,
        double meanFrameTimeMs,
        double p50FrameTimeMs,
        double p95FrameTimeMs,
        double p99FrameTimeMs,
        double maxFrameTimeMs,
        double stdDevFrameTimeMs) {

    public static FrameStats of(double[] frameTimesMs, int count) {
        if (count <= 0) {
            return new FrameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        double[] sorted = Arrays.copyOf(frameTimesMs, count);
        Arrays.sort(sorted);
        double total = 0.0;
        for (int i = 0; i < count; i++) {
            total += sorted[i];
        }
        double mean = total / count;
        double variance = 0.0;
        for (int i = 0; i < count; i++) {
            double d = sorted[i] - mean;
            variance += d * d;
        }
        variance /= count;
        return new FrameStats(
                count,
                total / 1000.0,
                total > 0 ? count * 1000.0 / total : 0.0,
                lowFps(sorted, 0.01),
                lowFps(sorted, 0.001),
                mean,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                sorted[count - 1],
                Math.sqrt(variance));
    }

    /** 1000 / mean of the slowest {@code fraction} of frames; {@code sorted} is ascending. */
    static double lowFps(double[] sorted, double fraction) {
        int n = Math.max(1, (int) Math.ceil(sorted.length * fraction));
        double sum = 0.0;
        for (int i = sorted.length - n; i < sorted.length; i++) {
            sum += sorted[i];
        }
        double mean = sum / n;
        return mean > 0 ? 1000.0 / mean : 0.0;
    }

    /** Nearest-rank percentile; {@code sorted} is ascending. */
    static double percentile(double[] sorted, double p) {
        int rank = (int) Math.ceil(p * sorted.length);
        return sorted[Math.min(sorted.length - 1, Math.max(0, rank - 1))];
    }
}
