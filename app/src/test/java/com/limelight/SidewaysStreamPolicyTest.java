package com.limelight;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SidewaysStreamPolicyTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void sanitizesUnknownModesToOff() {
        assertEquals(SidewaysStreamPolicy.MODE_OFF, SidewaysStreamPolicy.sanitizeMode(null));
        assertEquals(SidewaysStreamPolicy.MODE_OFF, SidewaysStreamPolicy.sanitizeMode("future"));
        assertEquals(SidewaysStreamPolicy.MODE_CW, SidewaysStreamPolicy.sanitizeMode("cw"));
        assertEquals(SidewaysStreamPolicy.MODE_CCW, SidewaysStreamPolicy.sanitizeMode("ccw"));
    }

    @Test
    public void compatibilityGateKeepsPocOffExternalAndGlModes() {
        assertTrue(SidewaysStreamPolicy.shouldApply("cw", false, 0));
        assertTrue(SidewaysStreamPolicy.shouldApply("ccw", false, 0));
        assertFalse(SidewaysStreamPolicy.shouldApply("cw", true, 0));
        assertFalse(SidewaysStreamPolicy.shouldApply("cw", false, 1));
        assertFalse(SidewaysStreamPolicy.shouldApply("off", false, 0));
    }

    @Test
    public void activeModeSwapsLogicalDimensions() {
        assertEquals(2400, SidewaysStreamPolicy.logicalWidth(1080, 2400, true));
        assertEquals(1080, SidewaysStreamPolicy.logicalHeight(1080, 2400, true));
        assertEquals(1080, SidewaysStreamPolicy.logicalWidth(1080, 2400, false));
        assertEquals(2400, SidewaysStreamPolicy.logicalHeight(1080, 2400, false));
    }

    @Test
    public void rotationsAndPositionSlotsAreDeterministic() {
        assertEquals(90f, SidewaysStreamPolicy.rotationDegrees("cw"), EPSILON);
        assertEquals(-90f, SidewaysStreamPolicy.rotationDegrees("ccw"), EPSILON);
        assertEquals(0f, SidewaysStreamPolicy.rotationDegrees("off"), EPSILON);
        assertEquals("sideways_cw", SidewaysStreamPolicy.positionSlot("cw"));
        assertEquals("sideways_ccw", SidewaysStreamPolicy.positionSlot("ccw"));
        assertNull(SidewaysStreamPolicy.positionSlot("off"));
    }

    @Test
    public void clockwiseCoordinatesRoundTrip() {
        float[] physical = SidewaysStreamPolicy.logicalToPhysical(
                "cw", 1700f, 240f, 1080f, 2400f);
        assertArrayEquals(new float[]{840f, 1700f}, physical, EPSILON);
        float[] logical = SidewaysStreamPolicy.physicalToLogical(
                "cw", physical[0], physical[1], 1080f, 2400f);
        assertArrayEquals(new float[]{1700f, 240f}, logical, EPSILON);
    }

    @Test
    public void counterClockwiseCoordinatesRoundTrip() {
        float[] physical = SidewaysStreamPolicy.logicalToPhysical(
                "ccw", 1700f, 240f, 1080f, 2400f);
        assertArrayEquals(new float[]{240f, 700f}, physical, EPSILON);
        float[] logical = SidewaysStreamPolicy.physicalToLogical(
                "ccw", physical[0], physical[1], 1080f, 2400f);
        assertArrayEquals(new float[]{1700f, 240f}, logical, EPSILON);
    }
}
