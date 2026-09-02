package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.TestLogSuppressor;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class PreferenceStringValuesTest {
    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    @Test
    public void parsersRejectMalformedAndOutOfRangeValues() {
        PreferenceStringValues.Resolution resolution =
                PreferenceStringValues.parseResolution("1280x", PreferenceConfiguration.DEFAULT_RESOLUTION);
        assertEquals(1280, resolution.width);
        assertEquals(720, resolution.height);

        assertEquals(60f, PreferenceStringValues.parsePositiveFiniteFloat("not-a-number", 60f), 0f);
        assertEquals(60f, PreferenceStringValues.parsePositiveFiniteFloat("NaN", 60f), 0f);
        assertEquals(60f, PreferenceStringValues.parsePositiveFiniteFloat("-30", 60f), 0f);
        assertEquals(2, PreferenceStringValues.parseBoundedInt("99", 2, 0, 5));
        assertEquals(1, PreferenceStringValues.parseBoundedInt("1", 2, 0, 5));
    }

    @Test
    public void readPreferencesFallsBackForMalformedStringBackedSettings() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("malformed-preference-values", Context.MODE_PRIVATE);
        prefs.edit().clear()
                .putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, "brokenxvalue")
                .putString(PreferenceConfiguration.FPS_PREF_STRING, "NaN")
                .putString("render_mode_list", "99")
                .putString("mouse_mode_list", "not-an-int")
                .commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context, prefs);

        assertEquals(1280, config.width);
        assertEquals(720, config.height);
        assertEquals(60f, config.fps, 0f);
        assertEquals(0, config.renderMode);
        assertTrue(config.enableMultiTouchScreen);
        assertFalse(config.touchscreenTrackpad);
    }
}
