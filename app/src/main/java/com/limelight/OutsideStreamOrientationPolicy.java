package com.limelight;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import androidx.preference.PreferenceManager;

import com.limelight.utils.ExternalDisplayControlActivity;

/** Applies the user's orientation policy to normal Artemis screens, never to the stream itself. */
public final class OutsideStreamOrientationPolicy {
    public static final String PREF_KEY = "list_outside_stream_orientation";
    public static final String MODE_FOLLOW_SYSTEM = "follow_system";
    public static final String MODE_PORTRAIT = "portrait";

    private OutsideStreamOrientationPolicy() {}

    public static String getMode(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            return preferences.getString(PREF_KEY, MODE_FOLLOW_SYSTEM);
        } catch (ClassCastException malformedPreference) {
            // A restored backup or downgrade can leave this key with a stale non-string type.
            // Every normal Activity reads it on create/resume, so recover to the safe default
            // instead of letting one malformed preference crash the non-stream UI repeatedly.
            preferences.edit().remove(PREF_KEY).apply();
            return MODE_FOLLOW_SYSTEM;
        }
    }

    public static void apply(Activity activity) {
        apply(activity, getMode(activity));
    }

    public static void apply(Activity activity, String mode) {
        if (activity == null || !shouldManageActivity(activity)) {
            return;
        }

        int requested = requestedOrientationForMode(mode);
        if (activity.getRequestedOrientation() != requested) {
            activity.setRequestedOrientation(requested);
        }
    }

    static boolean shouldManageActivity(Activity activity) {
        // Game owns stream orientation. The trampoline has no user-facing UI, and the external
        // display controller belongs to the separate external-display flow rather than phone UI.
        return !(activity instanceof Game)
                && !(activity instanceof ShortcutTrampoline)
                && !(activity instanceof ExternalDisplayControlActivity);
    }

    static int requestedOrientationForMode(String mode) {
        if (MODE_PORTRAIT.equals(mode)) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }

        // FULL_USER explicitly releases the stream's fixed landscape/portrait request while still
        // respecting the user's Android rotation-lock preference. It is supported by every Android
        // version Artemis currently targets (minSdk 21).
        return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
    }
}
