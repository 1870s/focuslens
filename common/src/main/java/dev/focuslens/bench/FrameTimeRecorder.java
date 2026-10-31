package dev.focuslens.bench;

/**
 * Measures the time between the starts of consecutive frames (Minecraft#renderFrame). Main thread.
 * Always tracks a short rolling average for the overlay; stores every frame only while recording.
 */
public final class FrameTimeRecorder {
    private static long lastFrameNanos;
    private static double[] recorded = new double[8192];
    private static int recordedCount;
    private static boolean recording;

    private static final int ROLLING = 60;
    private static final double[] rolling = new double[ROLLING];
    private static int rollingIndex;
    private static int rollingCount;

    private FrameTimeRecorder() {
    }

    public static void onFrameStart() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            double ms = (now - lastFrameNanos) / 1.0e6;
            rolling[rollingIndex] = ms;
            rollingIndex = (rollingIndex + 1) % ROLLING;
            rollingCount = Math.min(ROLLING, rollingCount + 1);
            if (recording) {
                if (recordedCount == recorded.length) {
                    recorded = java.util.Arrays.copyOf(recorded, recorded.length * 2);
                }
                recorded[recordedCount++] = ms;
            }
        }
        lastFrameNanos = now;
    }

    public static void startRecording() {
        recordedCount = 0;
        recording = true;
    }

    /** Stops recording and returns a copy of the recorded frame times in milliseconds. */
    public static double[] stopRecording() {
        recording = false;
        return java.util.Arrays.copyOf(recorded, recordedCount);
    }

    public static double rollingAverageMs() {
        if (rollingCount == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < rollingCount; i++) sum += rolling[i];
        return sum / rollingCount;
    }

    public static double rollingMaxMs() {
        double max = 0.0;
        for (int i = 0; i < rollingCount; i++) max = Math.max(max, rolling[i]);
        return max;
    }
}
