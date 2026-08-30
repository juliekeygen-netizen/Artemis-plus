from pathlib import Path


def patch(path, old, new):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'anchor missing in {path}: {old[:100]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8', newline='\n')

# Gear: session reset means BOTH orientation slots; style the new reset prompt with shared UI.
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyBoardController.java'
patch(path,
      'import com.limelight.ui.FloatingControlPositionStore;\n',
      'import com.limelight.ui.ArtemisEditorUi;\nimport com.limelight.ui.FloatingControlPositionStore;\n')
patch(path,
      '''                new AlertDialog.Builder(context)
                        .setTitle("Reset position?")
                        .setMessage("Reset the on-screen settings button to its default position?")
                        .setPositiveButton("Reset", (dialog, which) -> resetConfigureButtonPosition())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();''',
      '''                AlertDialog resetDialog = ArtemisEditorUi.builder(context, "Reset position?")
                        .setMessage("Reset the on-screen settings button to its default position?")
                        .setPositiveButton("Reset", (dialog, which) -> resetConfigureButtonPosition())
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
                resetDialog.setOnShowListener(ignored ->
                        ArtemisEditorUi.styleDialog(resetDialog, context, 420));
                resetDialog.show();''')
patch(path,
      '''FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);
        }
    }

    private void resetConfigureButtonPosition()''',
      '''FloatingControlPositionStore.clearAllOrientations(context, SETTINGS_POSITION_ID);
        }
    }

    private void resetConfigureButtonPosition()''')

# The Actions list is intentionally scrollable: cap the whole dialog instead of allowing a tall
# wrap-content window to fight the footer on landscape phones.
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/ArtemisActionButtonFactory.java'
patch(path,
      'ArtemisEditorUi.styleDialog(dialog, context, 520, 600, false)',
      'ArtemisEditorUi.styleDialog(dialog, context, 520, 600, true)')

# Group membership must describe an actually snapped cluster, not merely anything still inside the
# larger 30px attraction radius. The proportional tolerance keeps gaps from scaled groups attached.
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelper.java'
patch(path,
      '''        int horizontalTolerance = Math.max(
                SPACING_THRESHOLD, Math.round(minWidth * GROUP_SIZE_TOLERANCE_RATIO));
        int verticalTolerance = Math.max(
                SPACING_THRESHOLD, Math.round(minHeight * GROUP_SIZE_TOLERANCE_RATIO));''',
      '''        int horizontalTolerance = Math.max(
                SNAP_THRESHOLD, Math.round(minWidth * GROUP_SIZE_TOLERANCE_RATIO));
        int verticalTolerance = Math.max(
                SNAP_THRESHOLD, Math.round(minHeight * GROUP_SIZE_TOLERANCE_RATIO));''')

path = 'app/src/test/java/com/limelight/binding/input/virtual_controller/keyboard/LayoutSnappingHelperTest.java'
patch(path,
      '''    @Test
    public void doesNotGroupBeyondSpacingAdjustmentRangeAtNormalSize() {
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                171, 100, 40, 40));
    }

    @Test
    public void doesNotGroupDistantAlignedControls()''',
      '''    @Test
    public void doesNotGroupBeyondSpacingAdjustmentRangeAtNormalSize() {
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                171, 100, 40, 40));
    }

    @Test
    public void nearbyButUnsnappedControlsDoNotBecomeAGroup() {
        // The 30px attraction radius is for pulling a moving control into the 4px snap gap.
        // Once stationary, a normal-size 20px gap must not silently become grouped.
        assertFalse(LayoutSnappingHelper.areGrouped(
                100, 100, 40, 40,
                160, 100, 40, 40));
    }

    @Test
    public void doesNotGroupDistantAlignedControls()''')

# Keep shared editor dialogs inside narrow windows too. The previous hard 320dp minimum could exceed
# the actual 92%-of-window cap on compact split-screen/landscape layouts.
path = 'app/src/main/java/com/limelight/ui/ArtemisEditorUi.java'
patch(path,
      '''            int width = Math.min(dp(context, maxWidthDp), Math.round(screenWidth * 0.92f));
            int height = WindowManager.LayoutParams.WRAP_CONTENT;''',
      '''            int widthCap = Math.max(1, Math.round(screenWidth * 0.92f));
            int width = Math.min(dp(context, maxWidthDp), widthCap);
            int minimumWidth = Math.min(dp(context, 320), widthCap);
            int height = WindowManager.LayoutParams.WRAP_CONTENT;''')
patch(path,
      '            window.setLayout(Math.max(dp(context, 320), width), height);',
      '            window.setLayout(Math.max(minimumWidth, width), height);')

# Regression coverage for the new cross-session floating-position preference/store.
Path('app/src/test/java/com/limelight/ui').mkdir(parents=True, exist_ok=True)
Path('app/src/test/java/com/limelight/ui/FloatingControlPositionStoreTest.java').write_text(r'''package com.limelight.ui;

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
    public void clearAllOrientationsRemovesPortraitAndLandscapeCoordinates() {
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
                .commit();

        FloatingControlPositionStore.clearAllOrientations(context, id);

        assertFalse(preferences.contains(id + "_portrait_saved"));
        assertFalse(preferences.contains(id + "_portrait_x"));
        assertFalse(preferences.contains(id + "_portrait_y"));
        assertFalse(preferences.contains(id + "_landscape_saved"));
        assertFalse(preferences.contains(id + "_landscape_x"));
        assertFalse(preferences.contains(id + "_landscape_y"));
    }
}
''', encoding='utf-8', newline='\n')

# Make the new persistence regression part of the hard Artemis Plus gate. This workflow is a YAML
# folded scalar, so the test arguments are ordinary separate lines rather than shell '\\' lines.
path = '.github/workflows/android-ci.yml'
patch(path,
      '''          --tests "com.limelight.nvstream.http.PairingManagerRetryTest"
          --stacktrace''',
      '''          --tests "com.limelight.nvstream.http.PairingManagerRetryTest"
          --tests "com.limelight.ui.FloatingControlPositionStoreTest"
          --stacktrace''')

# Cleanup is performed through the GitHub connector after the generated product/test changes land.
print('UI v4 lifecycle hardening applied')
