package com.limelight;

/** Keeps requested PiP ratios inside Android's supported 1:2.39..2.39:1 range. */
final class PipAspectRatioHelper {
    static final int MAX_RATIO_NUMERATOR = 239;
    static final int MAX_RATIO_DENOMINATOR = 100;

    private PipAspectRatioHelper() {
    }

    static int[] clamp(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        double ratio = (double) safeWidth / (double) safeHeight;
        double maxRatio = (double) MAX_RATIO_NUMERATOR / MAX_RATIO_DENOMINATOR;
        double minRatio = 1.0 / maxRatio;

        if (ratio > maxRatio) {
            return new int[]{MAX_RATIO_NUMERATOR, MAX_RATIO_DENOMINATOR};
        }
        if (ratio < minRatio) {
            return new int[]{MAX_RATIO_DENOMINATOR, MAX_RATIO_NUMERATOR};
        }
        return new int[]{safeWidth, safeHeight};
    }
}
