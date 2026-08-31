package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared normalized-position persistence for Artemis floating controls and visual layout modes. */
public final class FloatingControlPositionStore {
    public static final String PREFS = "ArtemisPlusFloatingControlPositions";
    public static final String RESET_BETWEEN_SESSIONS_KEY =
            "checkbox_reset_floating_controls_between_sessions";

    private static final Map<Context, String> SESSION_LAYOUT_SLOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FloatingControlPositionStore() {}

    public static boolean shouldResetBetweenSessions(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(RESET_BETWEEN_SESSIONS_KEY, false);
    }

    /**
     * Starts a real stream session. Resetting belongs to the Game lifecycle rather than to an
     * individual View attach, because controls can be detached/re-attached while one stream is
     * still running. The dedicated floating-position store contains no unrelated preferences, so a
     * single synchronous clear resets every control and every visual-layout slot before controls
     * post their restore callbacks.
     */
    public static void beginStreamSession(Context context) {
        SESSION_LAYOUT_SLOTS.remove(context);
        if (shouldResetBetweenSessions(context)) {
            prefs(context).edit().clear().commit();
        }
    }

    /**
     * Override the normal portrait/landscape slot for this stream Activity. Sideways mode keeps the
     * Android configuration physically portrait, so it needs its own logical CW/CCW persistence
     * slots rather than overwriting the user's ordinary portrait control positions.
     */
    public static void setSessionLayoutSlot(Context context, String slot) {
        if (context == null) return;
        String normalized = normalizeSlot(slot);
        if (normalized == null) {
            SESSION_LAYOUT_SLOTS.remove(context);
        } else {
            SESSION_LAYOUT_SLOTS.put(context, normalized);
        }
    }

    public static void clearSessionLayoutSlot(Context context) {
        if (context != null) SESSION_LAYOUT_SLOTS.remove(context);
    }

    static String resolveLayoutSlot(Context context) {
        String sessionSlot = SESSION_LAYOUT_SLOTS.get(context);
        if (sessionSlot != null) return sessionSlot;
        int orientation = context.getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_PORTRAIT ? "portrait" : "landscape";
    }

    public static void save(View view, String identity) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof View) || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - view.getWidth());
        int maxY = Math.max(0, parentView.getHeight() - view.getHeight());
        if (maxX <= 0 || maxY <= 0) return;
        String id = identity == null ? identityForView(view) : identity;
        prefs(view.getContext()).edit()
                .putBoolean(key(view.getContext(), id, "saved"), true)
                .putFloat(key(view.getContext(), id, "x"), clamp(view.getX() / maxX))
                .putFloat(key(view.getContext(), id, "y"), clamp(view.getY() / maxY))
                .apply();
    }

    public static boolean restore(View view, String identity) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof View) || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        String id = identity == null ? identityForView(view) : identity;
        SharedPreferences preferences = prefs(view.getContext());
        if (!preferences.getBoolean(key(view.getContext(), id, "saved"), false)) return false;
        View parentView = (View) parent;
        int maxX = Math.max(0, parentView.getWidth() - view.getWidth());
        int maxY = Math.max(0, parentView.getHeight() - view.getHeight());
        if (maxX <= 0 || maxY <= 0) return false;
        view.setX(clamp(preferences.getFloat(key(view.getContext(), id, "x"), 0f)) * maxX);
        view.setY(clamp(preferences.getFloat(key(view.getContext(), id, "y"), 0f)) * maxY);
        return true;
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
        for (String orientation : new String[]{
                "portrait", "landscape", "sideways_cw", "sideways_ccw"}) {
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
        return identity + "_" + resolveLayoutSlot(context) + "_" + axis;
    }

    private static String normalizeSlot(String slot) {
        if ("sideways_cw".equals(slot) || "sideways_ccw".equals(slot)) return slot;
        return null;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
