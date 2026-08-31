package com.limelight.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class FloatingControlPositionStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY).commit();
    }

    @After
    public void tearDown() {
        context.getSharedPreferences(FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY).commit();
    }

    @Test
    public void resetBetweenSessionsDefaultsOffAndCanBeEnabled() {
        assertFalse(FloatingControlPositionStore.shouldResetBetweenSessions(context));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY, true).commit();
        assertTrue(FloatingControlPositionStore.shouldResetBetweenSessions(context));
    }

    @Test
    public void beginStreamSessionKeepsPositionsWhenResetIsDisabled() {
        SharedPreferences preferences = context.getSharedPreferences(
                FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE);
        preferences.edit().putBoolean("floatingMenuButton_portrait_saved", true).commit();

        FloatingControlPositionStore.beginStreamSession(context);

        assertTrue(preferences.contains("floatingMenuButton_portrait_saved"));
    }

    @Test
    public void beginStreamSessionClearsAllControlsWhenResetIsEnabled() {
        SharedPreferences preferences = context.getSharedPreferences(
                FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean("floatingMenuButton_portrait_saved", true)
                .putBoolean("keyboardSettingsButton_landscape_saved", true)
                .commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY, true).commit();

        FloatingControlPositionStore.beginStreamSession(context);

        assertFalse(preferences.contains("floatingMenuButton_portrait_saved"));
        assertFalse(preferences.contains("keyboardSettingsButton_landscape_saved"));
    }

    @Test
    public void clearAllOrientationsRemovesPhysicalAndSidewaysCoordinates() {
        String id = "keyboardSettingsButton";
        SharedPreferences preferences = context.getSharedPreferences(
                FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean(id + "_portrait_saved", true)
                .putFloat(id + "_portrait_x", .25f)
                .putFloat(id + "_portrait_y", .30f)
                .putBoolean(id + "_landscape_saved", true)
                .putFloat(id + "_landscape_x", .75f)
                .putFloat(id + "_landscape_y", .70f)
                .putBoolean(id + "_sideways_cw_saved", true)
                .putFloat(id + "_sideways_cw_x", .40f)
                .putFloat(id + "_sideways_cw_y", .20f)
                .putBoolean(id + "_sideways_ccw_saved", true)
                .putFloat(id + "_sideways_ccw_x", .60f)
                .putFloat(id + "_sideways_ccw_y", .80f)
                .commit();

        FloatingControlPositionStore.clearAllOrientations(context, id);

        assertFalse(preferences.contains(id + "_portrait_saved"));
        assertFalse(preferences.contains(id + "_portrait_x"));
        assertFalse(preferences.contains(id + "_portrait_y"));
        assertFalse(preferences.contains(id + "_landscape_saved"));
        assertFalse(preferences.contains(id + "_landscape_x"));
        assertFalse(preferences.contains(id + "_landscape_y"));
        assertFalse(preferences.contains(id + "_sideways_cw_saved"));
        assertFalse(preferences.contains(id + "_sideways_cw_x"));
        assertFalse(preferences.contains(id + "_sideways_cw_y"));
        assertFalse(preferences.contains(id + "_sideways_ccw_saved"));
        assertFalse(preferences.contains(id + "_sideways_ccw_x"));
        assertFalse(preferences.contains(id + "_sideways_ccw_y"));
    }
}
