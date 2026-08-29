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
