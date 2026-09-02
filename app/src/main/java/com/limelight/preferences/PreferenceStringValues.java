package com.limelight.preferences;

/** Pure parsing helpers for string-backed preferences that may survive old or damaged installs. */
final class PreferenceStringValues {
    private PreferenceStringValues() {
    }

    static final class Resolution {
        final int width;
        final int height;

        Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    static Resolution parseResolution(String value, String fallback) {
        Resolution parsed = tryParseResolution(value);
        if (parsed != null) {
            return parsed;
        }

        parsed = tryParseResolution(fallback);
        if (parsed == null) {
            throw new IllegalArgumentException("Fallback resolution must be valid");
        }
        return parsed;
    }

    private static Resolution tryParseResolution(String value) {
        if (value == null) {
            return null;
        }

        String[] parts = value.split("x", -1);
        if (parts.length != 2) {
            return null;
        }

        try {
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new Resolution(width, height);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static float parsePositiveFiniteFloat(String value, float fallback) {
        try {
            float parsed = Float.parseFloat(value);
            if (parsed > 0f && Float.isFinite(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return fallback;
    }

    static int parseBoundedInt(String value, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= min && parsed <= max) {
                return parsed;
            }
        } catch (NumberFormatException | NullPointerException ignored) {
        }
        return fallback;
    }
}
