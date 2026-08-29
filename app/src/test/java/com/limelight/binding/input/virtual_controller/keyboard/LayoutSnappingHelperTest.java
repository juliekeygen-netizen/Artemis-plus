package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
