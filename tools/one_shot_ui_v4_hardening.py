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
      '''                new AlertDialog.Builder(context)\n                        .setTitle("Reset position?")\n                        .setMessage("Reset the on-screen settings button to its default position?")\n                        .setPositiveButton("Reset", (dialog, which) -> resetConfigureButtonPosition())\n                        .setNegativeButton(android.R.string.cancel, null)\n                        .show();''',
      '''                AlertDialog resetDialog = ArtemisEditorUi.builder(context, "Reset position?")\n                        .setMessage("Reset the on-screen settings button to its default position?")\n                        .setPositiveButton("Reset", (dialog, which) -> resetConfigureButtonPosition())\n                        .setNegativeButton(android.R.string.cancel, null)\n                        .create();\n                resetDialog.setOnShowListener(ignored ->\n                        ArtemisEditorUi.styleDialog(resetDialog, context, 420));\n                resetDialog.show();''')
patch(path,
      'FloatingControlPositionStore.clearCurrentOrientation(context, SETTINGS_POSITION_ID);\n        }\n    }\n\n    private void resetConfigureButtonPosition()',
      'FloatingControlPositionStore.clearAllOrientations(context, SETTINGS_POSITION_ID);\n        }\n    }\n\n    private void resetConfigureButtonPosition()')

# The Actions list is intentionally scrollable: cap the whole dialog instead of allowing a tall
# wrap-content window to fight the footer on landscape phones.
path = 'app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/ArtemisActionButtonFactory.java'
patch(path,
      'ArtemisEditorUi.styleDialog(dialog, context, 520, 600, false)',
      'ArtemisEditorUi.styleDialog(dialog, context, 520, 600, true)')

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

# Make the new persistence regression part of the hard Artemis Plus gate.
path = '.github/workflows/android-ci.yml'
patch(path,
      '''            --tests "com.limelight.nvstream.http.PairingManagerRetryTest" \\\n            --stacktrace''',
      '''            --tests "com.limelight.nvstream.http.PairingManagerRetryTest" \\\n            --tests "com.limelight.ui.FloatingControlPositionStoreTest" \\\n            --stacktrace''')

# Self-clean one-shot machinery.
Path('tools/one_shot_ui_v4_hardening.py').unlink()
wf = Path('.github/workflows/one-shot-ui-v4-hardening.yml')
if wf.exists():
    wf.unlink()
print('UI v4 lifecycle hardening applied')
