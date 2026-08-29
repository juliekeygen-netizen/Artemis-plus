package com.limelight.binding.input.virtual_controller.keyboard;

import android.view.View;
import android.widget.FrameLayout;

public class LayoutSnappingHelper {
    private static final int SNAP_THRESHOLD = 10; // Pixels threshold for snapping
    private static final float OVERLAP_THRESHOLD = 0.5f; // 50% overlap required to trigger size matching
    private static final int SPACING_MIN = 4; // Minimum spacing between parallel edges
    private static final int SPACING_THRESHOLD = 30; // Maximum distance to trigger spacing adjustment

    // Group resize scales the entire cluster, including the inter-control gap. A fixed 10/30px
    // tolerance eventually disconnects a legitimately grouped pair after a large scale-up, so the
    // group tolerance grows with the controls while retaining the normal editor threshold as a
    // floor. 25% is comfortably above the 4px/default-button ratio but still rejects distant rows.
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
        /** Axis locks let the editor keep a snapped relationship until the user deliberately pulls away. */
        public boolean lockX;
        public boolean lockY;

        public SnapResult(int x, int y, int width, int height, boolean snapped, boolean resized, boolean adjustedSpacing) {
            this.newX = x;
            this.newY = y;
            this.newWidth = width;
            this.newHeight = height;
            this.didSnap = snapped;
            this.didResize = resized;
            this.didAdjustSpacing = adjustedSpacing;
        }
    }

    private static boolean isOverlapping(View view1, int x1, int y1, View view2, int x2, int y2) {
        int right1 = x1 + view1.getWidth();
        int bottom1 = y1 + view1.getHeight();
        int right2 = x2 + view2.getWidth();
        int bottom2 = y2 + view2.getHeight();

        int overlapX = Math.min(right1, right2) - Math.max(x1, x2);
        int overlapY = Math.min(bottom1, bottom2) - Math.max(y1, y2);

        if (overlapX <= 0 || overlapY <= 0) return false;

        float overlapArea = overlapX * overlapY;
        float view1Area = view1.getWidth() * view1.getHeight();
        float view2Area = view2.getWidth() * view2.getHeight();

        float overlapPercentage;
        if (view1Area > view2Area) {
            overlapPercentage = overlapArea / view2Area;
        } else {
            overlapPercentage = overlapArea / view1Area;
        }

        return overlapPercentage >= OVERLAP_THRESHOLD;
    }

    private static boolean hasParallelEdges(int edge1Start, int edge1End, int edge2Start, int edge2End) {
        int overlapStart = Math.max(edge1Start, edge2Start);
        int overlapEnd = Math.min(edge1End, edge2End);
        return overlapEnd - overlapStart > Math.min(edge1End - edge1Start, edge2End - edge2Start) * 0.5;
    }

    public static boolean areGrouped(View first, View second) {
        if (first == null || second == null || first == second ||
                first.getVisibility() != View.VISIBLE || second.getVisibility() != View.VISIBLE ||
                !(first.getLayoutParams() instanceof FrameLayout.LayoutParams) ||
                !(second.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return false;
        }

        FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) first.getLayoutParams();
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) second.getLayoutParams();
        return areGrouped(
                a.leftMargin, a.topMargin, a.width, a.height,
                b.leftMargin, b.topMargin, b.width, b.height);
    }

    /** Pure geometry form used by grouped-resize regression tests. */
    static boolean areGrouped(int aLeft, int aTop, int aWidth, int aHeight,
                              int bLeft, int bTop, int bWidth, int bHeight) {
        aWidth = Math.max(1, aWidth);
        aHeight = Math.max(1, aHeight);
        bWidth = Math.max(1, bWidth);
        bHeight = Math.max(1, bHeight);

        int aRight = aLeft + aWidth;
        int aBottom = aTop + aHeight;
        int bRight = bLeft + bWidth;
        int bBottom = bTop + bHeight;

        int verticalOverlap = Math.min(aBottom, bBottom) - Math.max(aTop, bTop);
        int horizontalOverlap = Math.min(aRight, bRight) - Math.max(aLeft, bLeft);

        int minHeight = Math.min(aHeight, bHeight);
        int minWidth = Math.min(aWidth, bWidth);

        int horizontalGapTolerance = Math.max(
                SPACING_THRESHOLD,
                Math.round(minWidth * GROUP_SIZE_TOLERANCE_RATIO));
        int verticalGapTolerance = Math.max(
                SPACING_THRESHOLD,
                Math.round(minHeight * GROUP_SIZE_TOLERANCE_RATIO));

        boolean sideBySide = verticalOverlap >= minHeight * GROUP_PARALLEL_OVERLAP &&
                (Math.abs(aRight - bLeft) <= horizontalGapTolerance ||
                        Math.abs(bRight - aLeft) <= horizontalGapTolerance);

        boolean stacked = horizontalOverlap >= minWidth * GROUP_PARALLEL_OVERLAP &&
                (Math.abs(aBottom - bTop) <= verticalGapTolerance ||
                        Math.abs(bBottom - aTop) <= verticalGapTolerance);

        return sideBySide || stacked;
    }

    public static SnapResult calculateSnappedPosition(View movingView, View[] otherViews, int proposedX, int proposedY) {
        int snappedX = proposedX;
        int snappedY = proposedY;
        int newWidth = ((FrameLayout.LayoutParams) movingView.getLayoutParams()).width;
        int newHeight = ((FrameLayout.LayoutParams) movingView.getLayoutParams()).height;
        boolean didSnap = false;
        boolean didResize = false;
        boolean didAdjustSpacing = false;
        boolean lockX = false;
        boolean lockY = false;

        FrameLayout.LayoutParams movingParams = (FrameLayout.LayoutParams) movingView.getLayoutParams();
        int movingWidth = movingParams.width;
        int movingHeight = movingParams.height;

        for (View otherView : otherViews) {
            if (otherView == movingView || otherView.getVisibility() != View.VISIBLE) {
                continue;
            }

            FrameLayout.LayoutParams otherParams = (FrameLayout.LayoutParams) otherView.getLayoutParams();

            if (isOverlapping(movingView, proposedX, proposedY, otherView, otherParams.leftMargin, otherParams.topMargin)) {
                newWidth = otherView.getWidth();
                newHeight = otherView.getHeight();
                didResize = true;
            }

            if (hasParallelEdges(proposedY, proposedY + movingHeight,
                    otherParams.topMargin, otherParams.topMargin + otherView.getHeight())) {
                int leftDistance = Math.abs(proposedX - (otherParams.leftMargin + otherView.getWidth()));
                if (leftDistance > SPACING_MIN && leftDistance < SPACING_THRESHOLD) {
                    snappedX = otherParams.leftMargin + otherView.getWidth() + SPACING_MIN;
                    didAdjustSpacing = true;
                    lockX = true;
                }

                int rightDistance = Math.abs((proposedX + movingWidth) - otherParams.leftMargin);
                if (rightDistance > SPACING_MIN && rightDistance < SPACING_THRESHOLD) {
                    snappedX = otherParams.leftMargin - SPACING_MIN - movingWidth;
                    didAdjustSpacing = true;
                    lockX = true;
                }
            }

            if (hasParallelEdges(proposedX, proposedX + movingWidth,
                    otherParams.leftMargin, otherParams.leftMargin + otherView.getWidth())) {
                int topDistance = Math.abs(proposedY - (otherParams.topMargin + otherView.getHeight()));
                if (topDistance > SPACING_MIN && topDistance < SPACING_THRESHOLD) {
                    snappedY = otherParams.topMargin + otherView.getHeight() + SPACING_MIN;
                    didAdjustSpacing = true;
                    lockY = true;
                }

                int bottomDistance = Math.abs((proposedY + movingHeight) - otherParams.topMargin);
                if (bottomDistance > SPACING_MIN && bottomDistance < SPACING_THRESHOLD) {
                    snappedY = otherParams.topMargin - SPACING_MIN - movingHeight;
                    didAdjustSpacing = true;
                    lockY = true;
                }
            }

            if (Math.abs(proposedX - otherParams.leftMargin) < SNAP_THRESHOLD) {
                snappedX = otherParams.leftMargin;
                didSnap = true;
                lockX = true;
            }
            if (Math.abs((proposedX + movingWidth) - (otherParams.leftMargin + otherView.getWidth())) < SNAP_THRESHOLD) {
                snappedX = otherParams.leftMargin + otherView.getWidth() - movingWidth;
                didSnap = true;
                lockX = true;
            }
            if (Math.abs(proposedY - otherParams.topMargin) < SNAP_THRESHOLD) {
                snappedY = otherParams.topMargin;
                didSnap = true;
                lockY = true;
            }
            if (Math.abs((proposedY + movingHeight) - (otherParams.topMargin + otherView.getHeight())) < SNAP_THRESHOLD) {
                snappedY = otherParams.topMargin + otherView.getHeight() - movingHeight;
                didSnap = true;
                lockY = true;
            }
        }

        SnapResult result = new SnapResult(
                snappedX, snappedY, newWidth, newHeight, didSnap, didResize, didAdjustSpacing);
        result.lockX = lockX;
        result.lockY = lockY;
        return result;
    }
}
