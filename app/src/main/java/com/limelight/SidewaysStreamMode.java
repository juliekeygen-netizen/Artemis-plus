package com.limelight;

import android.content.res.Configuration;

/**
 * Policy and coordinate transforms for the experimental fake-portrait stream mode.
 *
 * The Android Activity remains physically portrait while the in-stream visual root uses a
 * landscape-sized logical canvas rotated by 90 degrees. Keep every compatibility decision and
 * physical/raw-to-logical mapping here so input, layout, persistence, and orientation code cannot
 * silently disagree about what "sideways" means.
 */
public final class SidewaysStreamMode {
    public static final String PREF_KEY = "list_sideways_stream_mode";

    public static final String MODE_OFF = "off";
    public static final String MODE_CW = "sideways_cw";
    public static final String MODE_CCW = "sideways_ccw";

    private SidewaysStreamMode() {
    }

    public static String normalize(String mode) {
        if (MODE_CW.equals(mode) || MODE_CCW.equals(mode)) {
            return mode;
        }
        return MODE_OFF;
    }

    /**
     * Sideways mode is deliberately conservative for the first POC. 3D rendering and external
     * displays retain the existing rendering/orientation path until they have dedicated validation.
     */
    public static String resolveSessionMode(String requestedMode,
                                            int renderMode,
                                            boolean onExternalDisplay) {
        if (renderMode != 0 || onExternalDisplay) {
            return MODE_OFF;
        }
        return normalize(requestedMode);
    }

    public static boolean isActive(String mode) {
        return MODE_CW.equals(mode) || MODE_CCW.equals(mode);
    }

    /** Sideways 2D uses TextureView for rotation; keep HDR on the normal SurfaceView path. */
    public static boolean shouldForceSdr(String mode) {
        return isActive(mode);
    }

    public static float rotationDegrees(String mode) {
        if (MODE_CW.equals(mode)) {
            return 90f;
        }
        if (MODE_CCW.equals(mode)) {
            return -90f;
        }
        return 0f;
    }

    public static int logicalWidth(int physicalWidth, int physicalHeight, String mode) {
        return isActive(mode) ? physicalHeight : physicalWidth;
    }

    public static int logicalHeight(int physicalWidth, int physicalHeight, String mode) {
        return isActive(mode) ? physicalWidth : physicalHeight;
    }

    /** Maximum top/left coordinate that keeps a child fully inside its logical parent. */
    public static float clampChildPosition(float position, int parentSize, int childSize) {
        int max = Math.max(0, parentSize - childSize);
        return Math.max(0f, Math.min(position, max));
    }

    /**
     * Converts absolute screen/raw coordinates into coordinates in the rotated logical stream root.
     * Values are intentionally not clamped because drag/gesture delta calculations must continue to
     * work when a finger temporarily leaves the root bounds.
     */
    public static LogicalPoint physicalRawToLogical(float rawX,
                                                    float rawY,
                                                    float physicalRootScreenX,
                                                    float physicalRootScreenY,
                                                    int physicalWidth,
                                                    int physicalHeight,
                                                    String mode) {
        float physicalX = rawX - physicalRootScreenX;
        float physicalY = rawY - physicalRootScreenY;

        if (MODE_CW.equals(mode)) {
            return new LogicalPoint(
                    physicalY,
                    physicalWidth - physicalX);
        }
        if (MODE_CCW.equals(mode)) {
            return new LogicalPoint(
                    physicalHeight - physicalY,
                    physicalX);
        }
        return new LogicalPoint(rawX, rawY);
    }

    /** Separate saved floating-control positions for physical and both sideways visual modes. */
    public static String positionSlot(String mode, int physicalOrientation) {
        if (MODE_CW.equals(mode)) {
            return MODE_CW;
        }
        if (MODE_CCW.equals(mode)) {
            return MODE_CCW;
        }
        return physicalOrientation == Configuration.ORIENTATION_PORTRAIT
                ? "portrait"
                : "landscape";
    }

    public static final class LogicalPoint {
        public final float x;
        public final float y;

        public LogicalPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
