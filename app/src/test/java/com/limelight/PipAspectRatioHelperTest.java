package com.limelight;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class PipAspectRatioHelperTest {
    @Test
    public void keepsNormalRatiosUntouched() {
        assertArrayEquals(new int[]{16, 9}, PipAspectRatioHelper.clamp(16, 9));
        assertArrayEquals(new int[]{9, 16}, PipAspectRatioHelper.clamp(9, 16));
    }

    @Test
    public void clampsExtremeLandscapeAndPortraitRatios() {
        assertArrayEquals(new int[]{239, 100}, PipAspectRatioHelper.clamp(32, 9));
        assertArrayEquals(new int[]{100, 239}, PipAspectRatioHelper.clamp(9, 32));
    }

    @Test
    public void neverReturnsZeroSizedRatio() {
        assertArrayEquals(new int[]{1, 1}, PipAspectRatioHelper.clamp(0, 0));
    }
}
