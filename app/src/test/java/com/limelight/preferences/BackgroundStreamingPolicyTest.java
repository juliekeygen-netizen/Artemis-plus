package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundStreamingPolicyTest {
    @Test
    public void parsesSupportedTimeoutsAndManualMode() {
        assertEquals(30_000L, BackgroundStreamingPolicy.parseTimeoutMillis("30000"));
        assertEquals(120_000L, BackgroundStreamingPolicy.parseTimeoutMillis("120000"));
        assertEquals(300_000L, BackgroundStreamingPolicy.parseTimeoutMillis("300000"));
        assertEquals(0L, BackgroundStreamingPolicy.parseTimeoutMillis("0"));
    }

    @Test
    public void invalidTimeoutFallsBackSafely() {
        assertEquals(BackgroundStreamingPolicy.DEFAULT_TIMEOUT_MS,
                BackgroundStreamingPolicy.parseTimeoutMillis(null));
        assertEquals(BackgroundStreamingPolicy.DEFAULT_TIMEOUT_MS,
                BackgroundStreamingPolicy.parseTimeoutMillis("garbage"));
        assertEquals(BackgroundStreamingPolicy.DEFAULT_TIMEOUT_MS,
                BackgroundStreamingPolicy.parseTimeoutMillis("-1"));
    }

    @Test
    public void fastResumeOnlyOwnsOrdinaryBackgroundStop() {
        assertTrue(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_DISABLED, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, true, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, false, true, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, false, false, true, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, false, false, false, true));
        assertFalse(BackgroundStreamingPolicy.shouldUseFastResume(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false, false, false, false));
    }
}
