package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import org.junit.Test;

public class SidewaysStreamModeTest {
    @Test
    public void unsupportedSessionsFallBackToOff() {
        assertEquals(SidewaysStreamMode.MODE_CW,
                SidewaysStreamMode.resolveSessionMode(SidewaysStreamMode.MODE_CW, 0, false));
        assertEquals(SidewaysStreamMode.MODE_OFF,
                SidewaysStreamMode.resolveSessionMode(SidewaysStreamMode.MODE_CW, 1, false));
        assertEquals(SidewaysStreamMode.MODE_OFF,
                SidewaysStreamMode.resolveSessionMode(SidewaysStreamMode.MODE_CCW, 0, true));
        assertEquals(SidewaysStreamMode.MODE_OFF,
                SidewaysStreamMode.resolveSessionMode("future_value", 0, false));
    }

    @Test
    public void logicalDimensionsSwapOnlyWhenSideways() {
        assertEquals(100, SidewaysStreamMode.logicalWidth(100, 200, SidewaysStreamMode.MODE_OFF));
        assertEquals(200, SidewaysStreamMode.logicalHeight(100, 200, SidewaysStreamMode.MODE_OFF));
        assertEquals(200, SidewaysStreamMode.logicalWidth(100, 200, SidewaysStreamMode.MODE_CW));
        assertEquals(100, SidewaysStreamMode.logicalHeight(100, 200, SidewaysStreamMode.MODE_CCW));
    }

    @Test
    public void clockwiseRawCoordinatesMapIntoLogicalLandscape() {
        SidewaysStreamMode.LogicalPoint point = SidewaysStreamMode.physicalRawToLogical(
                85f, 70f, 10f, 20f, 100, 200, SidewaysStreamMode.MODE_CW);
        assertEquals(50f, point.x, 0.001f);
        assertEquals(25f, point.y, 0.001f);
    }

    @Test
    public void counterClockwiseRawCoordinatesMapIntoLogicalLandscape() {
        SidewaysStreamMode.LogicalPoint point = SidewaysStreamMode.physicalRawToLogical(
                85f, 70f, 10f, 20f, 100, 200, SidewaysStreamMode.MODE_CCW);
        assertEquals(150f, point.x, 0.001f);
        assertEquals(75f, point.y, 0.001f);
    }

    @Test
    public void offModePreservesAbsoluteRawCoordinates() {
        SidewaysStreamMode.LogicalPoint point = SidewaysStreamMode.physicalRawToLogical(
                85f, 70f, 10f, 20f, 100, 200, SidewaysStreamMode.MODE_OFF);
        assertEquals(85f, point.x, 0.001f);
        assertEquals(70f, point.y, 0.001f);
        assertFalse(SidewaysStreamMode.isActive(SidewaysStreamMode.MODE_OFF));
        assertTrue(SidewaysStreamMode.isActive(SidewaysStreamMode.MODE_CW));
    }

    @Test
    public void sidewaysTextureViewSessionsForceSdrOnlyWhenActive() {
        assertFalse(SidewaysStreamMode.shouldForceSdr(SidewaysStreamMode.MODE_OFF));
        assertTrue(SidewaysStreamMode.shouldForceSdr(SidewaysStreamMode.MODE_CW));
        assertTrue(SidewaysStreamMode.shouldForceSdr(SidewaysStreamMode.MODE_CCW));
    }

    @Test
    public void physicalCornersMapToLogicalLandscapeCorners() {
        assertPoint(0f, 100f, SidewaysStreamMode.physicalRawToLogical(
                0f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));
        assertPoint(0f, 0f, SidewaysStreamMode.physicalRawToLogical(
                100f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));
        assertPoint(200f, 100f, SidewaysStreamMode.physicalRawToLogical(
                0f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));
        assertPoint(200f, 0f, SidewaysStreamMode.physicalRawToLogical(
                100f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CW));

        assertPoint(200f, 0f, SidewaysStreamMode.physicalRawToLogical(
                0f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));
        assertPoint(200f, 100f, SidewaysStreamMode.physicalRawToLogical(
                100f, 0f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));
        assertPoint(0f, 0f, SidewaysStreamMode.physicalRawToLogical(
                0f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));
        assertPoint(0f, 100f, SidewaysStreamMode.physicalRawToLogical(
                100f, 200f, 0f, 0f, 100, 200, SidewaysStreamMode.MODE_CCW));
    }

    private static void assertPoint(float x, float y, SidewaysStreamMode.LogicalPoint point) {
        assertEquals(x, point.x, 0.001f);
        assertEquals(y, point.y, 0.001f);
    }

    @Test
    public void logicalChildPositionClampUsesParentCoordinateSpace() {
        assertEquals(0f, SidewaysStreamMode.clampChildPosition(-20f, 200, 40), 0.001f);
        assertEquals(75f, SidewaysStreamMode.clampChildPosition(75f, 200, 40), 0.001f);
        assertEquals(160f, SidewaysStreamMode.clampChildPosition(190f, 200, 40), 0.001f);
        assertEquals(0f, SidewaysStreamMode.clampChildPosition(20f, 30, 40), 0.001f);
    }

    @Test
    public void floatingPositionSlotsDoNotCollide() {
        assertEquals("portrait", SidewaysStreamMode.positionSlot(
                SidewaysStreamMode.MODE_OFF, Configuration.ORIENTATION_PORTRAIT));
        assertEquals("landscape", SidewaysStreamMode.positionSlot(
                SidewaysStreamMode.MODE_OFF, Configuration.ORIENTATION_LANDSCAPE));
        assertEquals("sideways_cw", SidewaysStreamMode.positionSlot(
                SidewaysStreamMode.MODE_CW, Configuration.ORIENTATION_PORTRAIT));
        assertEquals("sideways_ccw", SidewaysStreamMode.positionSlot(
                SidewaysStreamMode.MODE_CCW, Configuration.ORIENTATION_PORTRAIT));
    }
}
