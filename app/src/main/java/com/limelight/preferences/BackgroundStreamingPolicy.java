package com.limelight.preferences;

import android.os.Build;

/**
 * Pure policy for the stream Activity's background behavior. Keeping these decisions out of
 * Activity callbacks makes it much harder for PiP, configuration changes, explicit disconnects,
 * and ordinary app backgrounding to accidentally share the same teardown path.
 */
public final class BackgroundStreamingPolicy {
    public static final String MODE_DISABLED = "disabled";
    public static final String MODE_FAST_RESUME = "fast_resume";
    public static final String MODE_KEEP_ALIVE = "keep_alive";

    public static final long TIMEOUT_UNTIL_MANUAL_DISCONNECT = 0L;
    public static final long DEFAULT_TIMEOUT_MS = 120_000L;

    private BackgroundStreamingPolicy() {
    }

    public static boolean isFastResume(String mode) {
        return MODE_FAST_RESUME.equals(mode);
    }

    public static boolean isKeepAlive(String mode) {
        return MODE_KEEP_ALIVE.equals(mode);
    }

    public static boolean usesBackgroundTimeout(String mode) {
        return isFastResume(mode) || isKeepAlive(mode);
    }

    public static boolean isKeepAlivePlatformSupported(int sdkInt, int renderMode) {
        // MediaCodec.setOutputSurface() was added in Android 6.0. Keep Alive is intentionally
        // limited to the normal 2D MediaCodec path (SurfaceView or sideways TextureView); Artemis'
        // stereo modes own a separate GL Surface lifecycle and transparently fall back to Fast Resume.
        return sdkInt >= Build.VERSION_CODES.M && renderMode == 0;
    }

    public static long parseTimeoutMillis(String value) {
        if (value == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : DEFAULT_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private static boolean isOrdinaryBackgroundTransition(boolean finishing,
                                                          boolean changingConfigurations,
                                                          boolean pipTransitionExpected,
                                                          boolean externalDisplay,
                                                          boolean multiWindow) {
        return !finishing &&
                !changingConfigurations &&
                !pipTransitionExpected &&
                !externalDisplay &&
                !multiWindow;
    }

    public static boolean shouldArmFastResumeBeforeSurfaceLoss(String mode,
                                                               boolean finishing,
                                                               boolean changingConfigurations,
                                                               boolean pipTransitionExpected,
                                                               boolean externalDisplay,
                                                               boolean multiWindow) {
        return isFastResume(mode) && isOrdinaryBackgroundTransition(
                finishing, changingConfigurations, pipTransitionExpected, externalDisplay, multiWindow);
    }

    public static boolean shouldArmKeepAliveBeforeSurfaceLoss(String mode,
                                                              boolean keepAliveSupported,
                                                              boolean finishing,
                                                              boolean changingConfigurations,
                                                              boolean pipTransitionExpected,
                                                              boolean externalDisplay,
                                                              boolean multiWindow) {
        return isKeepAlive(mode) && keepAliveSupported && isOrdinaryBackgroundTransition(
                finishing, changingConfigurations, pipTransitionExpected, externalDisplay, multiWindow);
    }

    public static boolean shouldUseFastResume(String mode,
                                               boolean finishing,
                                               boolean changingConfigurations,
                                               boolean inPictureInPicture,
                                               boolean externalDisplay) {
        return isFastResume(mode) &&
                !finishing &&
                !changingConfigurations &&
                !inPictureInPicture &&
                !externalDisplay;
    }

    public static boolean shouldUseKeepAlive(String mode,
                                             boolean keepAliveSupported,
                                             boolean finishing,
                                             boolean changingConfigurations,
                                             boolean inPictureInPicture,
                                             boolean externalDisplay) {
        return isKeepAlive(mode) && keepAliveSupported &&
                !finishing &&
                !changingConfigurations &&
                !inPictureInPicture &&
                !externalDisplay;
    }
}
