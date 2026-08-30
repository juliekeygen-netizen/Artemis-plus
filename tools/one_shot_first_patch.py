from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit(f"anchor missing in {path}: {old[:160]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"anchor not unique in {path}: {old[:160]!r}")
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Phase 0: UI Editor V4 floating-control lifecycle + long-hold hardening
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/ui/FloatingControlPositionStore.java"
replace_once(path,
'''    public static boolean shouldResetBetweenSessions(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(RESET_BETWEEN_SESSIONS_KEY, false);
    }

    public static void save(View view, String identity) {''',
'''    public static boolean shouldResetBetweenSessions(Context context) {
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

    public static void save(View view, String identity) {''')

path = "app/src/main/java/com/limelight/ui/PersistentPositionImageButton.java"
replace_once(path,
'''public class PersistentPositionImageButton extends ImageButton {
    private boolean sessionResetApplied;

    public PersistentPositionImageButton(Context context) { super(context); }''',
'''public class PersistentPositionImageButton extends ImageButton {
    public PersistentPositionImageButton(Context context) { super(context); }''')
replace_once(path,
'''        if (!sessionResetApplied) {
            sessionResetApplied = true;
            if (FloatingControlPositionStore.shouldResetBetweenSessions(getContext())) {
                FloatingControlPositionStore.clearAllOrientations(
                        getContext(), FloatingControlPositionStore.identityForView(this));
            }
        }
        post(() -> FloatingControlPositionStore.restore(this, null));''',
'''        // Session-wide reset is performed once by Game.onCreate(). A View attach is not a
        // session boundary and may happen repeatedly during one stream.
        post(() -> FloatingControlPositionStore.restore(this, null));''')

path = "app/src/main/java/com/limelight/Game.java"
replace_once(path,
'''import com.limelight.ui.ArtemisEditorUi;
import com.limelight.ui.StreamContainer;''',
'''import com.limelight.ui.ArtemisEditorUi;
import com.limelight.ui.FloatingControlPositionStore;
import com.limelight.ui.StreamContainer;''')
replace_once(path,
'''        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this);
        tombstonePrefs = Game.this.getSharedPreferences("DecoderTombstone", 0);''',
'''        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this);

        // This Activity is the stream-session owner in the current architecture. Reset persistent
        // floating positions exactly once here, before any stream controls are restored.
        FloatingControlPositionStore.beginStreamSession(this);

        tombstonePrefs = Game.this.getSharedPreferences("DecoderTombstone", 0);''')

# Pure gesture-state helper keeps the subtle pre-long-press movement rule independently testable.
write("app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/LongPressMoveGestureGuard.java", r'''package com.limelight.binding.input.virtual_controller.keyboard;

/**
 * Tracks whether a hold-to-move gesture stopped being a valid long press before the move timer
 * armed. This prevents an ordinary wandering hold from unexpectedly becoming a drag later.
 */
final class LongPressMoveGestureGuard {
    private boolean disqualified;

    void reset() {
        disqualified = false;
    }

    /**
     * @return true when pending long-press timers must be cancelled.
     */
    boolean onMovePastSlop(boolean moveArmed) {
        if (!moveArmed) {
            disqualified = true;
            return true;
        }
        return false;
    }

    boolean canPerformClick(boolean moved, boolean moveArmed, boolean resetPromptShown) {
        return !disqualified && !moved && !moveArmed && !resetPromptShown;
    }

    boolean isDisqualified() {
        return disqualified;
    }
}
''')

path = "app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java"
replace_once(path,
'''    private static final String SETTINGS_POSITION_ID = "keyboardSettingsButton";
    private boolean configureSessionPositionPrepared;

    private final Vibrator vibrator;''',
'''    private static final String SETTINGS_POSITION_ID = "keyboardSettingsButton";
    // Keep the persisted reset target and the layout default literally identical by sharing these
    // physical-pixel anchors. They intentionally preserve the V4 position established previously.
    private static final int SETTINGS_DEFAULT_X_PX = 0;
    private static final int SETTINGS_DEFAULT_Y_PX = 15;

    private final Vibrator vibrator;''')
replace_once(path,
'''            private boolean moveArmed;
            private boolean moved;
            private boolean resetPromptShown;

            private final Runnable armMove = () -> {''',
'''            private boolean moveArmed;
            private boolean moved;
            private boolean resetPromptShown;
            private final LongPressMoveGestureGuard gestureGuard = new LongPressMoveGestureGuard();

            private final Runnable armMove = () -> {''')
replace_once(path,
'''                        moveArmed = false;
                        moved = false;
                        resetPromptShown = false;
                        handler.postDelayed(armMove, configureLongPressMs);''',
'''                        moveArmed = false;
                        moved = false;
                        resetPromptShown = false;
                        gestureGuard.reset();
                        handler.postDelayed(armMove, configureLongPressMs);''')
replace_once(path,
'''                        boolean beyondSlop = Math.hypot(dx, dy) > configureTouchSlop;
                        if (beyondSlop) {
                            handler.removeCallbacks(offerReset);
                        }
                        if (moveArmed && beyondSlop) {''',
'''                        boolean beyondSlop = Math.hypot(dx, dy) > configureTouchSlop;
                        if (beyondSlop) {
                            handler.removeCallbacks(offerReset);
                            // Movement before the long-press threshold is an ordinary cancelled
                            // gesture, not a drag waiting to arm later.
                            if (gestureGuard.onMovePastSlop(moveArmed)) {
                                handler.removeCallbacks(armMove);
                            }
                        }
                        if (moveArmed && beyondSlop) {''')
replace_once(path,
'''                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                                !moveArmed && !resetPromptShown) {
                            view.performClick();
                        }''',
'''                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                                gestureGuard.canPerformClick(moved, moveArmed, resetPromptShown)) {
                            view.performClick();
                        }''')
replace_once(path,
'''    private void prepareConfigureButtonSessionPosition() {
        if (configureSessionPositionPrepared) return;
        configureSessionPositionPrepared = true;
        if (FloatingControlPositionStore.shouldResetBetweenSessions(context)) {
            FloatingControlPositionStore.clearAllOrientations(context, SETTINGS_POSITION_ID);
        }
    }

    private void resetConfigureButtonPosition() {
        FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        if (buttonConfigure == null) return;
        buttonConfigure.setX(0f);
        buttonConfigure.setY(15f);
    }''',
'''    private void resetConfigureButtonPosition() {
        FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        if (buttonConfigure == null) return;
        buttonConfigure.setX(SETTINGS_DEFAULT_X_PX);
        buttonConfigure.setY(SETTINGS_DEFAULT_Y_PX);
    }''')
replace_once(path,
'''        configParams.leftMargin = 0; // old 20px anchor shifted 50 physical px left, clamped to edge
        configParams.topMargin = 15;
        frame_layout.addView(buttonConfigure, configParams);
        prepareConfigureButtonSessionPosition();
        buttonConfigure.post(() -> FloatingControlPositionStore.restore(''',
'''        configParams.leftMargin = SETTINGS_DEFAULT_X_PX; // old 20px anchor shifted left, clamped to edge
        configParams.topMargin = SETTINGS_DEFAULT_Y_PX;
        frame_layout.addView(buttonConfigure, configParams);
        buttonConfigure.post(() -> FloatingControlPositionStore.restore(''')

# ---------------------------------------------------------------------------
# Phase 1: deterministic orientation policy outside active streams
# ---------------------------------------------------------------------------
write("app/src/main/java/com/limelight/OutsideStreamOrientationPolicy.java", r'''package com.limelight;

import android.app.Activity;
import android.content.Context;
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
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY, MODE_FOLLOW_SYSTEM);
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
''')

write("app/src/main/java/com/limelight/ArtemisApplication.java", r'''package com.limelight;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.widget.Toast;

import com.limelight.profiles.ProfilesManager;

public class ArtemisApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // A single lifecycle policy avoids each non-stream Activity independently inheriting the
        // orientation request left behind by Game/OEM task state. Applying on both create and resume
        // also makes the return from a stream deterministic on devices such as OxygenOS.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                OutsideStreamOrientationPolicy.apply(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                OutsideStreamOrientationPolicy.apply(activity);
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });

        ProfilesManager profilesManager = ProfilesManager.getInstance();
        if (!profilesManager.load(this)) {
            Toast.makeText(this, R.string.profile_manager_failed_to_load, Toast.LENGTH_LONG).show();
        }
    }
}
''')

# Add the setting in UI settings and make Settings search the first root row.
path = "app/src/main/res/xml/preferences.xml"
replace_once(path,
'''    app:iconSpaceReserved="false">\n\n    <PreferenceCategory''',
'''    app:iconSpaceReserved="false">\n\n    <com.bytehamster.lib.preferencesearch.SearchPreference\n        android:key="searchPreference"\n        app:iconSpaceReserved="false" />\n\n    <PreferenceCategory''')
replace_once(path,
'''        <CheckBoxPreference
            android:defaultValue="false"
            android:key="checkbox_reset_floating_controls_between_sessions"''',
'''        <ListPreference
            android:defaultValue="follow_system"
            android:entries="@array/outside_stream_orientation_names"
            android:entryValues="@array/outside_stream_orientation_values"
            android:key="list_outside_stream_orientation"
            android:title="Outside stream orientation"
            android:summary="Controls Artemis screens when no stream is open. Follow system respects Android rotation lock; Portrait keeps Artemis menus upright."
            app:iconSpaceReserved="false" />
        <CheckBoxPreference
            android:defaultValue="false"
            android:key="checkbox_reset_floating_controls_between_sessions"''')

path = "app/src/main/res/values/arrays.xml"
replace_once(path,
'''<resources>
    <string-array name="resolution_names">''',
'''<resources>
    <string-array name="outside_stream_orientation_names">
        <item>Follow system</item>
        <item>Portrait</item>
    </string-array>
    <string-array name="outside_stream_orientation_values" translatable="false">
        <item>follow_system</item>
        <item>portrait</item>
    </string-array>

    <string-array name="resolution_names">''')

# ---------------------------------------------------------------------------
# Phase 2A: searchable settings, indexing the *visible runtime tree*
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/limelight/preferences/StreamSettings.java"
replace_once(path,
'''import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;''',
'''import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;''')
replace_once(path,
'''import com.google.gson.Gson;
import com.limelight.DebugInfoActivity;''',
'''import com.bytehamster.lib.preferencesearch.PreferenceItem;
import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreference;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener;
import com.google.gson.Gson;
import com.limelight.DebugInfoActivity;''')
replace_once(path,
'''import com.limelight.LimeLog;
import com.limelight.PcView;''',
'''import com.limelight.LimeLog;
import com.limelight.OutsideStreamOrientationPolicy;
import com.limelight.PcView;''')
replace_once(path,
'''public class StreamSettings extends AppCompatActivity {
    private PreferenceConfiguration previousPrefs;''',
'''public class StreamSettings extends AppCompatActivity implements SearchPreferenceResultListener {
    private PreferenceConfiguration previousPrefs;''')
replace_once(path,
'''    public static class SettingsFragment extends PreferenceFragmentCompat {''',
'''    @Override
    public void onSearchResultClicked(SearchPreferenceResult result) {
        result.closeSearchPage(this);
        if (prefsFragment != null) {
            prefsFragment.revealAndHighlightSearchResult(result);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {''')
replace_once(path,
'''            EditTextPreference customRefreshRatePref = findPreference(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING);
            if (customRefreshRatePref != null) {''',
'''            ListPreference outsideStreamOrientation = findPreference(OutsideStreamOrientationPolicy.PREF_KEY);
            if (outsideStreamOrientation != null) {
                outsideStreamOrientation.setOnPreferenceChangeListener((preference, newValue) -> {
                    // Apply the chosen value immediately; the lifecycle callback will also re-apply
                    // the persisted mode whenever a normal Artemis screen resumes.
                    OutsideStreamOrientationPolicy.apply(requireActivity(), String.valueOf(newValue));
                    return true;
                });
            }

            EditTextPreference customRefreshRatePref = findPreference(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING);
            if (customRefreshRatePref != null) {''')
replace_once(path,
'''                });
            }
        }

        private void removeEntryFromListAndSetValue''',
'''                });
            }

            configurePreferenceSearch(screen);
        }

        private void configurePreferenceSearch(PreferenceScreen screen) {
            SearchPreference searchPreference = findPreference("searchPreference");
            if (searchPreference == null) {
                return;
            }

            SearchConfiguration configuration = searchPreference.getSearchConfiguration();
            configuration.setActivity((AppCompatActivity) requireActivity());
            configuration.setBreadcrumbsEnabled(true);
            configuration.setHistoryId("artemis_settings");

            // Index the final runtime Preference tree rather than parsing the raw XML. Device- or
            // API-specific settings removed above therefore cannot appear as dead search results.
            indexVisiblePreferences(configuration, screen, null);
        }

        private void indexVisiblePreferences(SearchConfiguration configuration,
                                             PreferenceGroup group,
                                             String breadcrumb) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);
                if (preference instanceof SearchPreference) {
                    continue;
                }

                if (preference instanceof PreferenceGroup) {
                    String nextBreadcrumb = breadcrumb;
                    CharSequence title = preference.getTitle();
                    if (!TextUtils.isEmpty(title)) {
                        nextBreadcrumb = TextUtils.isEmpty(breadcrumb)
                                ? title.toString()
                                : breadcrumb + " > " + title;
                    }
                    indexVisiblePreferences(configuration, (PreferenceGroup) preference, nextBreadcrumb);
                    continue;
                }

                if (preference.getKey() == null ||
                        (preference.getTitle() == null && preference.getSummary() == null)) {
                    continue;
                }

                PreferenceItem item = configuration.indexItem(preference);
                if (!TextUtils.isEmpty(breadcrumb)) {
                    item.addBreadcrumb(breadcrumb);
                }
            }
        }

        void revealAndHighlightSearchResult(SearchPreferenceResult result) {
            Preference target = findPreference(result.getKey());
            if (target != null) {
                PreferenceGroup parent = target.getParent();
                while (parent != null) {
                    parent.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
                    parent = parent.getParent();
                }
            }
            result.highlight(this);
        }

        private void removeEntryFromListAndSetValue''')

# ---------------------------------------------------------------------------
# Regression coverage
# ---------------------------------------------------------------------------
path = "app/src/test/java/com/limelight/ui/FloatingControlPositionStoreTest.java"
replace_once(path,
'''    @Test
    public void clearAllOrientationsRemovesPortraitAndLandscapeCoordinates() {''',
'''    @Test
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
    public void clearAllOrientationsRemovesPortraitAndLandscapeCoordinates() {''')

write("app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/LongPressMoveGestureGuardTest.java", r'''package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LongPressMoveGestureGuardTest {
    @Test
    public void movementBeforeArmCancelsLongPressAndClick() {
        LongPressMoveGestureGuard guard = new LongPressMoveGestureGuard();
        guard.reset();

        assertTrue(guard.onMovePastSlop(false));
        assertTrue(guard.isDisqualified());
        assertFalse(guard.canPerformClick(false, false, false));
    }

    @Test
    public void movementAfterArmRemainsAValidDrag() {
        LongPressMoveGestureGuard guard = new LongPressMoveGestureGuard();
        guard.reset();

        assertFalse(guard.onMovePastSlop(true));
        assertFalse(guard.isDisqualified());
        assertFalse(guard.canPerformClick(true, true, false));
    }

    @Test
    public void ordinaryTapStillClicks() {
        LongPressMoveGestureGuard guard = new LongPressMoveGestureGuard();
        guard.reset();

        assertTrue(guard.canPerformClick(false, false, false));
    }
}
''')

write("app/src/test/java/com/limelight/OutsideStreamOrientationPolicyTest.java", r'''package com.limelight;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
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
}
''')

# Make the new regression tests gate the normal Android CI instead of only running in the inherited
# diagnostic suite.
path = ".github/workflows/android-ci.yml"
replace_once(path,
'''          --tests "com.limelight.ArtemisOrientationHelperTest"
          --tests "com.limelight.binding.input.virtual_controller.OscProfilesManagerTest"''',
'''          --tests "com.limelight.ArtemisOrientationHelperTest"
          --tests "com.limelight.OutsideStreamOrientationPolicyTest"
          --tests "com.limelight.binding.input.virtual_controller.OscProfilesManagerTest"''')
replace_once(path,
'''          --tests "com.limelight.binding.input.virtual_controller.keyboard.LayoutSnappingHelperTest"
          --tests "com.limelight.nvstream.http.PairingManagerRetryTest"''',
'''          --tests "com.limelight.binding.input.virtual_controller.keyboard.LayoutSnappingHelperTest"
          --tests "com.limelight.binding.input.virtual_controller.keyboard.LongPressMoveGestureGuardTest"
          --tests "com.limelight.nvstream.http.PairingManagerRetryTest"''')

print("First patch product changes and regression tests applied")
