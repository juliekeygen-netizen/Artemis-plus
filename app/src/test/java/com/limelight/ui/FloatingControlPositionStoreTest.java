package com.limelight.ui;

import static org.junit.Assert.assertEquals;
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
        FloatingControlPositionStore.clearSessionLayoutSlot(context);
        context.getSharedPreferences(FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY).commit();
    }

    @After
    public void tearDown() {
        FloatingControlPositionStore.clearSessionLayoutSlot(context);
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
                .putBoolean("keyboardSettingsButton_sideways_cw_saved", true)
                .commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY, true).commit();

        FloatingControlPositionStore.beginStreamSession(context);

        assertFalse(preferences.contains("floatingMenuButton_portrait_saved"));
        assertFalse(preferences.contains("keyboardSettingsButton_landscape_saved"));
        assertFalse(preferences.contains("keyboardSettingsButton_sideways_cw_saved"));
    }

    @Test
    public void sidewaysSessionSlotsAreDistinctFromPhysicalPortrait() {
        String ordinarySlot = FloatingControlPositionStore.resolveLayoutSlot(context);

        FloatingControlPositionStore.setSessionLayoutSlot(context, "sideways_cw");
        assertEquals("sideways_cw", FloatingControlPositionStore.resolveLayoutSlot(context));

        FloatingControlPositionStore.setSessionLayoutSlot(context, "sideways_ccw");
        assertEquals("sideways_ccw", FloatingControlPositionStore.resolveLayoutSlot(context));

        FloatingControlPositionStore.clearSessionLayoutSlot(context);
        assertEquals(ordinarySlot, FloatingControlPositionStore.resolveLayoutSlot(context));
    }

    @Test
    public void unsupportedSessionSlotFallsBackToOrientation() {
        String ordinarySlot = FloatingControlPositionStore.resolveLayoutSlot(context);
        FloatingControlPositionStore.setSessionLayoutSlot(context, "future_mode");
        assertEquals(ordinarySlot, FloatingControlPositionStore.resolveLayoutSlot(context));
    }

    @Test
    public void clearAllOrientationsRemovesNormalAndSidewaysCoordinates() {
        String id = "keyboardSettingsButton";
        SharedPreferences preferences = context.getSharedPreferences(
                FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (String slot : new String[]{"portrait", "landscape", "sideways_cw", "sideways_ccw"}) {
            editor.putBoolean(id + "_" + slot + "_saved", true)
                    .putFloat(id + "_" + slot + "_x", .25f)
                    .putFloat(id + "_" + slot + "_y", .75f);
        }
        editor.commit();

        FloatingControlPositionStore.clearAllOrientations(context, id);

        for (String slot : new String[]{"portrait", "landscape", "sideways_cw", "sideways_ccw"}) {
            assertFalse(preferences.contains(id + "_" + slot + "_saved"));
            assertFalse(preferences.contains(id + "_" + slot + "_x"));
            assertFalse(preferences.contains(id + "_" + slot + "_y"));
        }
    }
}
