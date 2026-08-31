package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class LayoutSnappingHelperTest {
    @Test
    public void groupsSideBySideControlsAtMoveSnapSpacing() {
        assertTrue(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                144, 100, 40, 40));
    }

    @Test
    public void groupsStackedControlsWithinSnapTolerance() {
        assertTrue(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                102, 149, 40, 40));
    }

    @Test
    public void scaledMoveSnapGapRemainsGrouped() {
        assertTrue(LayoutSnappingHelper.areGrouped(
                100, 100, 80, 80,
                200, 100, 80, 80));
    }

    @Test
    public void veryLargeUniformScaleKeepsProportionalGapGrouped() {
        // A 4px gap on a 40px button becomes 40px after a 10x uniform group scale.
        assertTrue(LayoutSnappingHelper.areGrouped(
                100, 100, 400, 400,
                540, 100, 400, 400));
    }

    @Test
    public void doesNotGroupBeyondSpacingAdjustmentRangeAtNormalSize() {
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                171, 100, 40, 40));
    }

    @Test
    public void nearbyButUnsnappedControlsDoNotBecomeAGroup() {
        // The 30px attraction radius is for pulling a moving control into the 4px snap gap.
        // Once stationary, a normal-size 20px gap must not silently become grouped.
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                160, 100, 40, 40));
    }

    @Test
    public void doesNotGroupDistantAlignedControls() {
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                200, 100, 40, 40));
    }

    @Test
    public void doesNotGroupDiagonalControlsWithoutParallelOverlap() {
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                144, 145, 40, 40));
    }

    @Test
    public void sideSpacingSnapLocksHorizontalAxisForMoveHysteresis() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 40, 40);

        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving,
                new View[]{anchor},
                146,
                100);

        assertTrue(result.didAdjustSpacing || result.didSnap);
        assertTrue(result.lockX);
    }

    @Test
    public void stackedSpacingSnapLocksVerticalAxisForMoveHysteresis() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 40, 40);

        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving,
                new View[]{anchor},
                100,
                146);

        assertTrue(result.didAdjustSpacing || result.didSnap);
        assertTrue(result.lockY);
    }

    @Test
    public void mixedSizeAdjacentControlsSnapWithoutResizing() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 90, 40);
        View anchor = sizedView(context, 120, 100, 40, 60);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 27, 108);
        assertTrue(result.lockX);
        assertFalse(result.didResize);
        assertEquals(26, result.newX);
        assertEquals(90, result.newWidth);
        assertEquals(40, result.newHeight);
    }

    @Test
    public void nearestCandidateWinsInsteadOfLaterViewOrder() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View near = sizedView(context, 100, 100, 40, 40);
        View farther = sizedView(context, 107, 100, 40, 40);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{near, farther}, 102, 100);
        assertEquals(100, result.newX);
    }

    @Test
    public void equalScoreCandidatesUseStableTieBreakRegardlessOfViewOrder() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View left = sizedView(context, 100, 100, 40, 40);
        View right = sizedView(context, 110, 100, 40, 40);

        LayoutSnappingHelper.SnapResult forward = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{left, right}, 105, 100);
        LayoutSnappingHelper.SnapResult reverse = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{right, left}, 105, 100);

        assertEquals(100, forward.newX);
        assertEquals(forward.newX, reverse.newX);
    }

    @Test
    public void centerAlignmentWorksForDifferentWidths() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 80, 40);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 121, 100);
        assertEquals(120, result.newX);
        assertTrue(result.lockX);
    }

    @Test
    public void centerAlignmentWorksForDifferentHeights() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 40, 80);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 100, 121);

        assertEquals(120, result.newY);
        assertTrue(result.lockY);
    }

    @Test
    public void spacingCandidatePreservesTheStandardGap() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 40, 40);

        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 55, 100);

        assertEquals(56, result.newX);
        assertTrue(result.didAdjustSpacing);
        assertFalse(result.didResize);
    }

    @Test
    public void overlapDoesNotResizeTheMovingControl() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 100, 100, 40, 60);
        View anchor = sizedView(context, 100, 100, 80, 40);

        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 100, 100);

        assertEquals(40, result.newWidth);
        assertEquals(60, result.newHeight);
        assertFalse(result.didResize);
    }

    @Test
    public void moveLockUsesReleaseHysteresisAndCanReattach() {
        assertTrue(LayoutSnappingHelper.shouldRetainAxisLock(70, 56, 28));
        assertFalse(LayoutSnappingHelper.shouldRetainAxisLock(84, 56, 28));

        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 40, 40);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 58, 100);

        assertEquals(56, result.newX);
        assertTrue(result.lockX);
    }

    @Test
    public void textExpansionMovesTheStackedRightHandBranchWithoutBreakingItsGroup() {
        int originalRight = 140;
        int expansionDelta = 80;
        int rightNeighborLeft = 144;
        int stackedBranchLeft = 144;

        assertTrue(LayoutSnappingHelper.shouldShiftForTextExpansion(originalRight, rightNeighborLeft));
        assertTrue(LayoutSnappingHelper.shouldShiftForTextExpansion(originalRight, stackedBranchLeft));
        assertFalse(LayoutSnappingHelper.shouldShiftForTextExpansion(originalRight, 100));
        assertTrue(LayoutSnappingHelper.areGrouped(
                rightNeighborLeft + expansionDelta, 100, 40, 40,
                stackedBranchLeft + expansionDelta, 144, 40, 40));
    }

    @Test
    public void groupMoveUsesOneClampedTranslationForEveryMember() {
        assertEquals(20, LayoutSnappingHelper.clampGroupTranslation(50, 100, 980, 1000));
        assertEquals(-100, LayoutSnappingHelper.clampGroupTranslation(-150, 100, 400, 1000));
        assertEquals(24, LayoutSnappingHelper.clampGroupTranslation(24, 100, 400, 1000));
    }

    private static View sizedView(Context context, int left, int top, int width, int height) {
        View view = new View(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        view.setLayoutParams(params);
        view.layout(left, top, left + width, top + height);
        return view;
    }
}
