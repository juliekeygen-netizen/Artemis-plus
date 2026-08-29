package com.limelight;

import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import org.junit.Test;

public class ArtemisOrientationHelperTest {
    @Test
    public void oppositeOrientationFlipsPortraitAndLandscape() {
        assertEquals(
                Configuration.ORIENTATION_PORTRAIT,
                ArtemisOrientationHelper.oppositeOrientation(Configuration.ORIENTATION_LANDSCAPE));
        assertEquals(
                Configuration.ORIENTATION_LANDSCAPE,
                ArtemisOrientationHelper.oppositeOrientation(Configuration.ORIENTATION_PORTRAIT));
    }

    @Test
    public void fixedRequestsDoNotDependOnUserRotationPreference() {
        assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ArtemisOrientationHelper.fixedRequestFor(Configuration.ORIENTATION_PORTRAIT));
        assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ArtemisOrientationHelper.fixedRequestFor(Configuration.ORIENTATION_LANDSCAPE));
    }

    @Test
    public void sensorFallbackStaysConstrainedToRequestedAxis() {
        assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
                ArtemisOrientationHelper.sensorRequestFor(Configuration.ORIENTATION_PORTRAIT));
        assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                ArtemisOrientationHelper.sensorRequestFor(Configuration.ORIENTATION_LANDSCAPE));
    }

    @Test
    public void windowBoundsWinWhenTheyClearlyDescribeOrientation() {
        assertEquals(
                Configuration.ORIENTATION_LANDSCAPE,
                ArtemisOrientationHelper.orientationFromDimensions(
                        2400, 1080, Configuration.ORIENTATION_PORTRAIT));
        assertEquals(
                Configuration.ORIENTATION_PORTRAIT,
                ArtemisOrientationHelper.orientationFromDimensions(
                        1080, 2400, Configuration.ORIENTATION_LANDSCAPE));
    }

    @Test
    public void squareOrUnavailableBoundsUseConfigurationFallback() {
        assertEquals(
                Configuration.ORIENTATION_PORTRAIT,
                ArtemisOrientationHelper.orientationFromDimensions(
                        1000, 1000, Configuration.ORIENTATION_PORTRAIT));
        assertEquals(
                Configuration.ORIENTATION_LANDSCAPE,
                ArtemisOrientationHelper.orientationFromDimensions(
                        0, 0, Configuration.ORIENTATION_UNDEFINED));
    }
}
