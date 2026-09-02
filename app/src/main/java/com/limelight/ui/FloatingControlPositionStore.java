package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;

import androidx.preference.PreferenceManager;

import com.limelight.Game;
import com.limelight.SidewaysStreamMode;

/** Shared portrait/landscape normalized-position persistence for Artemis floating controls. */
public final class FloatingControlPositionStore {
    public static final String PREFS = "ArtemisPlusFloatingControlPositions";
    public static final String RESET_BETWEEN_SESSIONS_KEY =
            "checkbox_reset_floating_controls_between_sessions";

    private FloatingControlPositionStore() {}

    public static boolean shouldResetBetweenSessions(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(RESET_BETWEEN_SESSIONS_KEY, false);
    }

    /**
     * Starts a real stream session. Resetting belongs to the Game lifecycle rather than to an
     * individual View attach, because controls can be detached/re-attached while one stream is
     * still running. The dedicated floating-position store contains no unrelated preferences, so a
     * single synchronous clear resets every control and both orientation slots before any control
     * posts its restore callback.
     */
    public static void beginStreamSession(Context context) {
        if (shouldResetBetweenSessions(context)) {
            prefs(context).edit().clear().commit();
        }
    }

    public static void save(View view, String identity) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof View) || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - view.getWidth());
        int maxY = Math.max(0, parentView.getHeight() - view.getHeight());
        if (maxX <= 0 || maxY <= 0) return;
        float normalizedX = view.getX() / maxX;
        float normalizedY = view.getY() / maxY;
        if (!isFinite(normalizedX) || !isFinite(normalizedY)) return;
        String id = identity == null ? identityForView(view) : identity;
        prefs(view.getContext()).edit()
                .putBoolean(key(view.getContext(), id, "saved"), true)
                .putFloat(key(view.getContext(), id, "x"), clamp(normalizedX))
                .putFloat(key(view.getContext(), id, "y"), clamp(normalizedY))
                .apply();
    }

    public static boolean restore(View view, String identity) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof View) || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        String id = identity == null ? identityForView(view) : identity;
        SharedPreferences preferences = prefs(view.getContext());
        try {
            if (!preferences.getBoolean(key(view.getContext(), id, "saved"), false)) return false;
            View parentView = (View) parent;
            int maxX = Math.max(0, parentView.getWidth() - view.getWidth());
            int maxY = Math.max(0, parentView.getHeight() - view.getHeight());
            if (maxX <= 0 || maxY <= 0) return false;
            float normalizedX = preferences.getFloat(key(view.getContext(), id, "x"), 0f);
            float normalizedY = preferences.getFloat(key(view.getContext(), id, "y"), 0f);
            if (!isFinite(normalizedX) || !isFinite(normalizedY)) {
                clearCurrentOrientation(view.getContext(), id);
                return false;
            }
            view.setX(clamp(normalizedX) * maxX);
            view.setY(clamp(normalizedY) * maxY);
            return true;
        } catch (ClassCastException malformedPreference) {
            // A stale backup, downgrade, or partial write can leave a key with the wrong type.
            // Recover this control's current slot instead of crashing from a posted restore callback.
            clearCurrentOrientation(view.getContext(), id);
            return false;
        }
    }

    public static void clearCurrentOrientation(Context context, String identity) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.remove(key(context, identity, "saved"));
        editor.remove(key(context, identity, "x"));
        editor.remove(key(context, identity, "y"));
        editor.apply();
    }

    public static void clearAllOrientations(Context context, String identity) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String orientation : new String[]{"portrait", "landscape",
                SidewaysStreamMode.MODE_CW, SidewaysStreamMode.MODE_CCW}) {
            editor.remove(identity + "_" + orientation + "_saved");
            editor.remove(identity + "_" + orientation + "_x");
            editor.remove(identity + "_" + orientation + "_y");
        }
        editor.apply();
    }

    public static String identityForView(View view) {
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Exception ignored) {
            return Integer.toString(view.getId());
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(Context context, String identity, String axis) {
        int orientation = context.getResources().getConfiguration().orientation;
        String mode = Game.instance != null
                ? Game.instance.getActiveSidewaysStreamMode()
                : SidewaysStreamMode.MODE_OFF;
        String orientationName = SidewaysStreamMode.positionSlot(mode, orientation);
        return identity + "_" + orientationName + "_" + axis;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
