package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import androidx.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class OutsideStreamOrientationPolicyTest {
    @Test
    public void followSystemUsesFullUserAndPortraitUsesPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                OutsideStreamOrientationPolicy.requestedOrientationForMode(
                        OutsideStreamOrientationPolicy.MODE_FOLLOW_SYSTEM));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                OutsideStreamOrientationPolicy.requestedOrientationForMode(
                        OutsideStreamOrientationPolicy.MODE_PORTRAIT));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                OutsideStreamOrientationPolicy.requestedOrientationForMode("unexpected"));
    }

    @Test
    public void applyReadsPreferenceAndUpdatesNormalActivity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString(OutsideStreamOrientationPolicy.PREF_KEY,
                        OutsideStreamOrientationPolicy.MODE_PORTRAIT)
                .commit();

        OutsideStreamOrientationPolicy.apply(activity);

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                activity.getRequestedOrientation());
    }

    @Test
    public void malformedPreferenceRecoversToFollowSystemWithoutCrashingActivityApply() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.edit()
                .putBoolean(OutsideStreamOrientationPolicy.PREF_KEY, true)
                .commit();

        OutsideStreamOrientationPolicy.apply(activity);

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                activity.getRequestedOrientation());
        assertFalse(preferences.contains(OutsideStreamOrientationPolicy.PREF_KEY));
    }
}
