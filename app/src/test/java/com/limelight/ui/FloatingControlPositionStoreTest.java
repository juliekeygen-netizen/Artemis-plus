package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.Game;
import com.limelight.SidewaysStreamMode;

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
        Game.instance = null;
        context.getSharedPreferences(FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(FloatingControlPositionStore.RESET_BETWEEN_SESSIONS_KEY).commit();
    }

    @After
    public void tearDown() {
        Game.instance = null;
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

    @Test
    public void restoreRejectsNonFiniteCoordinatesAndClearsCorruptSlot() {
        String id = "floatingMenuButton";
        String prefix = currentSlotPrefix(id);
        SharedPreferences preferences = positionPreferences();
        preferences.edit()
                .putBoolean(prefix + "_saved", true)
                .putFloat(prefix + "_x", Float.NaN)
                .putFloat(prefix + "_y", .5f)
                .commit();
        View view = createView(35f, 45f);

        assertFalse(FloatingControlPositionStore.restore(view, id));

        assertEquals(35f, view.getX(), 0f);
        assertEquals(45f, view.getY(), 0f);
        assertFalse(preferences.contains(prefix + "_saved"));
        assertFalse(preferences.contains(prefix + "_x"));
        assertFalse(preferences.contains(prefix + "_y"));
    }

    @Test
    public void restoreRejectsWrongPreferenceTypeAndClearsCorruptSlot() {
        String id = "keyboardSettingsButton";
        String prefix = currentSlotPrefix(id);
        SharedPreferences preferences = positionPreferences();
        preferences.edit()
                .putBoolean(prefix + "_saved", true)
                .putString(prefix + "_x", "not-a-float")
                .putFloat(prefix + "_y", .5f)
                .commit();
        View view = createView(20f, 30f);

        assertFalse(FloatingControlPositionStore.restore(view, id));

        assertEquals(20f, view.getX(), 0f);
        assertEquals(30f, view.getY(), 0f);
        assertFalse(preferences.contains(prefix + "_saved"));
        assertFalse(preferences.contains(prefix + "_x"));
        assertFalse(preferences.contains(prefix + "_y"));
    }

    @Test
    public void saveDoesNotOverwriteStoredPositionWithNonFiniteCoordinates() {
        String id = "floatingMenuButton";
        String prefix = currentSlotPrefix(id);
        SharedPreferences preferences = positionPreferences();
        preferences.edit()
                .putBoolean(prefix + "_saved", true)
                .putFloat(prefix + "_x", .25f)
                .putFloat(prefix + "_y", .75f)
                .commit();
        View view = createView(Float.NaN, 30f);

        FloatingControlPositionStore.save(view, id);

        assertTrue(preferences.getBoolean(prefix + "_saved", false));
        assertEquals(.25f, preferences.getFloat(prefix + "_x", -1f), 0f);
        assertEquals(.75f, preferences.getFloat(prefix + "_y", -1f), 0f);
    }

    private SharedPreferences positionPreferences() {
        return context.getSharedPreferences(FloatingControlPositionStore.PREFS, Context.MODE_PRIVATE);
    }

    private String currentSlotPrefix(String id) {
        String slot = SidewaysStreamMode.positionSlot(
                SidewaysStreamMode.MODE_OFF,
                context.getResources().getConfiguration().orientation);
        return id + "_" + slot;
    }

    private View createView(float x, float y) {
        FrameLayout parent = new FrameLayout(context);
        View view = new View(context);
        parent.addView(view, new FrameLayout.LayoutParams(100, 80));
        parent.layout(0, 0, 500, 400);
        view.layout(0, 0, 100, 80);
        view.setX(x);
        view.setY(y);
        return view;
    }
}
