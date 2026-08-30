package com.limelight.binding.input.virtual_controller.keyboard;

import android.view.View;
import android.widget.FrameLayout;

public class LayoutSnappingHelper {
    private static final int SNAP_THRESHOLD = 10;
    private static final int SPACING_MIN = 4;
    private static final int SPACING_THRESHOLD = 30;
    private static final float GROUP_SIZE_TOLERANCE_RATIO = 0.25f;
    private static final float GROUP_PARALLEL_OVERLAP = 0.40f;

    public static class SnapResult {
        public int newX;
        public int newY;
        public int newWidth;
        public int newHeight;
        public boolean didSnap;
        public boolean didResize;
        public boolean didAdjustSpacing;
        public boolean lockX;
        public boolean lockY;

        public SnapResult(int x, int y, int width, int height,
                          boolean snapped, boolean resized, boolean adjustedSpacing) {
            newX = x; newY = y; newWidth = width; newHeight = height;
            didSnap = snapped; didResize = resized; didAdjustSpacing = adjustedSpacing;
        }
    }

    private static final class Candidate {
        final int value;
        final int distance;
        final int priority;
        final boolean spacing;
        Candidate(int value, int distance, int priority, boolean spacing) {
            this.value = value; this.distance = distance; this.priority = priority; this.spacing = spacing;
        }
        boolean betterThan(Candidate other) {
            if (other == null) return true;
            if (distance != other.distance) return distance < other.distance;
            return priority < other.priority;
        }
    }

    public static boolean areGrouped(View first, View second) {
        if (first == null || second == null || first == second ||
                first.getVisibility() != View.VISIBLE || second.getVisibility() != View.VISIBLE ||
                !(first.getLayoutParams() instanceof FrameLayout.LayoutParams) ||
                !(second.getLayoutParams() instanceof FrameLayout.LayoutParams)) return false;
        FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) first.getLayoutParams();
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) second.getLayoutParams();
        return areGrouped(a.leftMargin, a.topMargin, a.width, a.height,
                b.leftMargin, b.topMargin, b.width, b.height);
    }

    static boolean areGrouped(int aLeft, int aTop, int aWidth, int aHeight,
                              int bLeft, int bTop, int bWidth, int bHeight) {
        aWidth = Math.max(1, aWidth); aHeight = Math.max(1, aHeight);
        bWidth = Math.max(1, bWidth); bHeight = Math.max(1, bHeight);
        int aRight = aLeft + aWidth, aBottom = aTop + aHeight;
        int bRight = bLeft + bWidth, bBottom = bTop + bHeight;
        int verticalOverlap = Math.min(aBottom, bBottom) - Math.max(aTop, bTop);
        int horizontalOverlap = Math.min(aRight, bRight) - Math.max(aLeft, bLeft);
        int minHeight = Math.min(aHeight, bHeight), minWidth = Math.min(aWidth, bWidth);
        int horizontalTolerance = Math.max(
                SNAP_THRESHOLD, Math.round(minWidth * GROUP_SIZE_TOLERANCE_RATIO));
        int verticalTolerance = Math.max(
                SNAP_THRESHOLD, Math.round(minHeight * GROUP_SIZE_TOLERANCE_RATIO));
        boolean sideBySide = verticalOverlap >= minHeight * GROUP_PARALLEL_OVERLAP &&
                (Math.abs(aRight - bLeft) <= horizontalTolerance ||
                        Math.abs(bRight - aLeft) <= horizontalTolerance);
        boolean stacked = horizontalOverlap >= minWidth * GROUP_PARALLEL_OVERLAP &&
                (Math.abs(aBottom - bTop) <= verticalTolerance ||
                        Math.abs(bBottom - aTop) <= verticalTolerance);
        return sideBySide || stacked;
    }

    public static SnapResult calculateSnappedPosition(View movingView, View[] otherViews,
                                                       int proposedX, int proposedY) {
        FrameLayout.LayoutParams movingParams = (FrameLayout.LayoutParams) movingView.getLayoutParams();
        int movingWidth = Math.max(1, movingParams.width);
        int movingHeight = Math.max(1, movingParams.height);
        Candidate bestX = null, bestY = null;

        for (View other : otherViews) {
            if (other == null || other == movingView || other.getVisibility() != View.VISIBLE ||
                    !(other.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) other.getLayoutParams();
            int otherWidth = Math.max(1, p.width), otherHeight = Math.max(1, p.height);
            int otherRight = p.leftMargin + otherWidth;
            int otherBottom = p.topMargin + otherHeight;

            int verticalOverlap = overlap(proposedY, proposedY + movingHeight,
                    p.topMargin, otherBottom);
            int horizontalOverlap = overlap(proposedX, proposedX + movingWidth,
                    p.leftMargin, otherRight);
            boolean parallelY = verticalOverlap >= Math.min(movingHeight, otherHeight) * 0.35f;
            boolean parallelX = horizontalOverlap >= Math.min(movingWidth, otherWidth) * 0.35f;

            if (parallelY) {
                bestX = consider(bestX, proposedX, otherRight + SPACING_MIN,
                        SPACING_THRESHOLD, 0, true);
                bestX = consider(bestX, proposedX, p.leftMargin - SPACING_MIN - movingWidth,
                        SPACING_THRESHOLD, 0, true);
            }
            bestX = consider(bestX, proposedX, p.leftMargin, SNAP_THRESHOLD, 1, false);
            bestX = consider(bestX, proposedX, otherRight - movingWidth, SNAP_THRESHOLD, 1, false);
            bestX = consider(bestX, proposedX,
                    p.leftMargin + otherWidth / 2 - movingWidth / 2,
                    SNAP_THRESHOLD, 2, false);

            if (parallelX) {
                bestY = consider(bestY, proposedY, otherBottom + SPACING_MIN,
                        SPACING_THRESHOLD, 0, true);
                bestY = consider(bestY, proposedY, p.topMargin - SPACING_MIN - movingHeight,
                        SPACING_THRESHOLD, 0, true);
            }
            bestY = consider(bestY, proposedY, p.topMargin, SNAP_THRESHOLD, 1, false);
            bestY = consider(bestY, proposedY, otherBottom - movingHeight, SNAP_THRESHOLD, 1, false);
            bestY = consider(bestY, proposedY,
                    p.topMargin + otherHeight / 2 - movingHeight / 2,
                    SNAP_THRESHOLD, 2, false);
        }

        int x = bestX == null ? proposedX : bestX.value;
        int y = bestY == null ? proposedY : bestY.value;
        boolean spacing = (bestX != null && bestX.spacing) || (bestY != null && bestY.spacing);
        boolean snap = (bestX != null && !bestX.spacing) || (bestY != null && !bestY.spacing);
        SnapResult result = new SnapResult(x, y, movingWidth, movingHeight, snap, false, spacing);
        result.lockX = bestX != null;
        result.lockY = bestY != null;
        return result;
    }

    private static Candidate consider(Candidate current, int proposed, int target,
                                      int threshold, int priority, boolean spacing) {
        int distance = Math.abs(proposed - target);
        if (distance > threshold) return current;
        Candidate candidate = new Candidate(target, distance, priority, spacing);
        return candidate.betterThan(current) ? candidate : current;
    }

    private static int overlap(int aStart, int aEnd, int bStart, int bEnd) {
        return Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
    }
}
