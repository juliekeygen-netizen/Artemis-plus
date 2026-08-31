package com.limelight.preferences;

/**
 * Pure policy for the stream Activity's background behavior. Keeping these decisions out of
 * Activity callbacks makes it much harder for PiP, configuration changes, explicit disconnects,
 * and ordinary app backgrounding to accidentally share the same teardown path.
 */
public final class BackgroundStreamingPolicy {
    public static final String MODE_DISABLED = "disabled";
    public static final String MODE_FAST_RESUME = "fast_resume";
    public static final String MODE_KEEP_ALIVE = "keep_alive"; // Reserved for the next experimental phase.

    public static final long TIMEOUT_UNTIL_MANUAL_DISCONNECT = 0L;
    public static final long DEFAULT_TIMEOUT_MS = 120_000L;

    private BackgroundStreamingPolicy() {
    }

    public static boolean isFastResume(String mode) {
        return MODE_FAST_RESUME.equals(mode);
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
}
