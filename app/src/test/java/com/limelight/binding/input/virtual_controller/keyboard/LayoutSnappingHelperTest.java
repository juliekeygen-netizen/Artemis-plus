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
    public void centerAlignmentWorksForDifferentWidths() {
        Context context = ApplicationProvider.getApplicationContext();
        View moving = sizedView(context, 0, 0, 40, 40);
        View anchor = sizedView(context, 100, 100, 80, 40);
        LayoutSnappingHelper.SnapResult result = LayoutSnappingHelper.calculateSnappedPosition(
                moving, new View[]{anchor}, 121, 100);
        assertEquals(120, result.newX);
        assertTrue(result.lockX);
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
