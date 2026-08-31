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

    private enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    /**
     * A possible attachment/alignment on one axis. Candidates are compared independently per
     * axis, rather than allowing the last inspected neighboring View to overwrite an earlier
     * match.
     */
    private enum Alignment {
        GAP(0),
        EDGE(1),
        CENTER(2);

        final int priority;

        Alignment(int priority) {
            this.priority = priority;
        }
    }

    private static final class Candidate {
        final Axis axis;
        final int target;
        final int distance;
        final Alignment alignment;

        Candidate(Axis axis, int target, int distance, Alignment alignment) {
            this.axis = axis;
            this.target = target;
            this.distance = distance;
            this.alignment = alignment;
        }

        boolean betterThan(Candidate other) {
            if (other == null) return true;
            if (distance != other.distance) return distance < other.distance;
            if (alignment.priority != other.alignment.priority) {
                return alignment.priority < other.alignment.priority;
            }
            if (axis != other.axis) return axis.ordinal() < other.axis.ordinal();
            // A numeric target and alignment kind are stable across View iteration order. This
            // final tie-break makes two equidistant neighbors choose the same outcome even if
            // the controller's child ordering changes after a refresh.
            if (target != other.target) return target < other.target;
            return alignment.ordinal() < other.alignment.ordinal();
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
                        SPACING_THRESHOLD, Axis.HORIZONTAL, Alignment.GAP);
                bestX = consider(bestX, proposedX, p.leftMargin - SPACING_MIN - movingWidth,
                        SPACING_THRESHOLD, Axis.HORIZONTAL, Alignment.GAP);
            }
            bestX = consider(bestX, proposedX, p.leftMargin, SNAP_THRESHOLD,
                    Axis.HORIZONTAL, Alignment.EDGE);
            bestX = consider(bestX, proposedX, otherRight - movingWidth, SNAP_THRESHOLD,
                    Axis.HORIZONTAL, Alignment.EDGE);
            bestX = consider(bestX, proposedX,
                    p.leftMargin + otherWidth / 2 - movingWidth / 2,
                    SNAP_THRESHOLD, Axis.HORIZONTAL, Alignment.CENTER);

            if (parallelX) {
                bestY = consider(bestY, proposedY, otherBottom + SPACING_MIN,
                        SPACING_THRESHOLD, Axis.VERTICAL, Alignment.GAP);
                bestY = consider(bestY, proposedY, p.topMargin - SPACING_MIN - movingHeight,
                        SPACING_THRESHOLD, Axis.VERTICAL, Alignment.GAP);
            }
            bestY = consider(bestY, proposedY, p.topMargin, SNAP_THRESHOLD,
                    Axis.VERTICAL, Alignment.EDGE);
            bestY = consider(bestY, proposedY, otherBottom - movingHeight, SNAP_THRESHOLD,
                    Axis.VERTICAL, Alignment.EDGE);
            bestY = consider(bestY, proposedY,
                    p.topMargin + otherHeight / 2 - movingHeight / 2,
                    SNAP_THRESHOLD, Axis.VERTICAL, Alignment.CENTER);
        }

        int x = bestX == null ? proposedX : bestX.target;
        int y = bestY == null ? proposedY : bestY.target;
        boolean spacing = (bestX != null && bestX.alignment == Alignment.GAP) ||
                (bestY != null && bestY.alignment == Alignment.GAP);
        boolean snap = (bestX != null && bestX.alignment != Alignment.GAP) ||
                (bestY != null && bestY.alignment != Alignment.GAP);
        SnapResult result = new SnapResult(x, y, movingWidth, movingHeight, snap, false, spacing);
        result.lockX = bestX != null;
        result.lockY = bestY != null;
        return result;
    }

    private static Candidate consider(Candidate current, int proposed, int target,
                                      int threshold, Axis axis, Alignment alignment) {
        int distance = Math.abs(proposed - target);
        if (distance > threshold) return current;
        Candidate candidate = new Candidate(axis, target, distance, alignment);
        return candidate.betterThan(current) ? candidate : current;
    }

    /** Pure move-lock seam shared with editor gesture handling and its hysteresis tests. */
    static boolean shouldRetainAxisLock(int proposed, int snappedCoordinate, int releaseThreshold) {
        return Math.abs(proposed - snappedCoordinate) < Math.max(0, releaseThreshold);
    }

    /** Whether a connected member belongs to the branch displaced by a wider text button. */
    static boolean shouldShiftForTextExpansion(int originalRight, int memberLeft) {
        return memberLeft >= originalRight - 6;
    }

    /** Clamp a shared group translation so every member remains within its parent axis. */
    static int clampGroupTranslation(int requestedDelta, int groupStart, int groupEnd,
                                     int parentExtent) {
        if (parentExtent <= 0) {
            return requestedDelta;
        }
        return Math.max(-groupStart, Math.min(requestedDelta, parentExtent - groupEnd));
    }

    private static int overlap(int aStart, int aEnd, int bStart, int bEnd) {
        return Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
    }
}
