package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(final String text);

    /**
     * Structured performance data for the stats overlay.
     * @param decodeTimeMs average decode time in milliseconds
     * @param renderTimeMs average render/total time in milliseconds
     * @param networkLatencyMs estimated network RTT in milliseconds
     * @param fps current rendered frames per second
     * @param codec name of the active video codec
     */
    default void onPerfStatsUpdate(float decodeTimeMs, float renderTimeMs, float networkLatencyMs,
                                   int fps, String codec) {
        // Default no-op for backward compatibility
    }
}
