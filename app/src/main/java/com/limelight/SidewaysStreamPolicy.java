package com.limelight;

/**
 * Deterministic policy/geometry helpers for Artemis Plus' experimental sideways stream mode.
 *
 * The Android Activity remains physically portrait while the in-stream visual root is sized as
 * logical landscape and rotated +/-90 degrees. Keeping the math here independent of Android View
 * state makes the risky geometry easy to regression-test before it reaches Game's input pipeline.
 */
public final class SidewaysStreamPolicy {
    public static final String PREF_KEY = "list_sideways_stream_mode";
    public static final String MODE_OFF = "off";
    public static final String MODE_CW = "cw";
    public static final String MODE_CCW = "ccw";

    public static final String POSITION_SLOT_CW = "sideways_cw";
    public static final String POSITION_SLOT_CCW = "sideways_ccw";

    private SidewaysStreamPolicy() {}

    public static String sanitizeMode(String mode) {
        if (MODE_CW.equals(mode) || MODE_CCW.equals(mode)) {
            return mode;
        }
        return MODE_OFF;
    }

    public static boolean isEnabled(String mode) {
        return !MODE_OFF.equals(sanitizeMode(mode));
    }

    /**
     * POC compatibility gate. External-display sessions and Artemis' GL/3D render modes stay on
     * their existing unrotated path until they are separately validated on hardware.
     */
    public static boolean shouldApply(String mode, boolean externalDisplay, int renderMode) {
        return isEnabled(mode) && !externalDisplay && renderMode == 0;
    }

    /** Android View rotation in degrees. Positive values rotate clockwise in screen coordinates. */
    public static float rotationDegrees(String mode) {
        String sanitized = sanitizeMode(mode);
        if (MODE_CW.equals(sanitized)) return 90f;
        if (MODE_CCW.equals(sanitized)) return -90f;
        return 0f;
    }

    public static int logicalWidth(int physicalWidth, int physicalHeight, boolean active) {
        return active ? Math.max(0, physicalHeight) : Math.max(0, physicalWidth);
    }

    public static int logicalHeight(int physicalWidth, int physicalHeight, boolean active) {
        return active ? Math.max(0, physicalWidth) : Math.max(0, physicalHeight);
    }

    public static String positionSlot(String mode) {
        String sanitized = sanitizeMode(mode);
        if (MODE_CW.equals(sanitized)) return POSITION_SLOT_CW;
        if (MODE_CCW.equals(sanitized)) return POSITION_SLOT_CCW;
        return null;
    }

    /** Map a physical-window point into the unrotated logical visual-root coordinates. */
    public static float[] physicalToLogical(String mode,
                                            float physicalX,
                                            float physicalY,
                                            float physicalWidth,
                                            float physicalHeight) {
        String sanitized = sanitizeMode(mode);
        if (MODE_CW.equals(sanitized)) {
            return new float[]{physicalY, physicalWidth - physicalX};
        }
        if (MODE_CCW.equals(sanitized)) {
            return new float[]{physicalHeight - physicalY, physicalX};
        }
        return new float[]{physicalX, physicalY};
    }

    /** Map an unrotated logical visual-root point into physical-window coordinates. */
    public static float[] logicalToPhysical(String mode,
                                            float logicalX,
                                            float logicalY,
                                            float physicalWidth,
                                            float physicalHeight) {
        String sanitized = sanitizeMode(mode);
        if (MODE_CW.equals(sanitized)) {
            return new float[]{physicalWidth - logicalY, logicalX};
        }
        if (MODE_CCW.equals(sanitized)) {
            return new float[]{logicalY, physicalHeight - logicalX};
        }
        return new float[]{logicalX, logicalY};
    }
}
