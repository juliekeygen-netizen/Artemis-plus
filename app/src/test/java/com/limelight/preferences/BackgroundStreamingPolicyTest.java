package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

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
    public void bothBackgroundModesUseTimeoutPreference() {
        assertTrue(BackgroundStreamingPolicy.usesBackgroundTimeout(
                BackgroundStreamingPolicy.MODE_FAST_RESUME));
        assertTrue(BackgroundStreamingPolicy.usesBackgroundTimeout(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE));
        assertFalse(BackgroundStreamingPolicy.usesBackgroundTimeout(
                BackgroundStreamingPolicy.MODE_DISABLED));
    }

    @Test
    public void keepAliveRequiresAndroidMAndNormal2dRenderer() {
        assertFalse(BackgroundStreamingPolicy.isKeepAlivePlatformSupported(
                Build.VERSION_CODES.LOLLIPOP_MR1, 0));
        assertTrue(BackgroundStreamingPolicy.isKeepAlivePlatformSupported(
                Build.VERSION_CODES.M, 0));
        assertFalse(BackgroundStreamingPolicy.isKeepAlivePlatformSupported(
                Build.VERSION_CODES.M, 1));
        assertFalse(BackgroundStreamingPolicy.isKeepAlivePlatformSupported(
                Build.VERSION_CODES.VANILLA_ICE_CREAM, 2));
    }

    @Test
    public void fastResumeArmingAvoidsPipAndVisibleMultiWindowTransitions() {
        assertTrue(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, true, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, false, false, true));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_DISABLED,
                false, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                true, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, true, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, false, true, false));
    }

    @Test
    public void keepAliveArmingHasSameLifecycleExclusions() {
        assertTrue(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true,
                false, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false,
                false, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true,
                false, false, true, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true,
                false, false, false, false, true));
        assertFalse(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, true,
                false, false, false, false, false));
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

    @Test
    public void keepAliveOnlyOwnsSupportedOrdinaryBackgroundStop() {
        assertTrue(BackgroundStreamingPolicy.shouldUseKeepAlive(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true,
                false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseKeepAlive(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false,
                false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseKeepAlive(
                BackgroundStreamingPolicy.MODE_FAST_RESUME, true,
                false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseKeepAlive(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true,
                true, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldUseKeepAlive(
                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true,
                false, false, true, false));
    }
}
