package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class KeyboardProfilesManagerTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("ArtemisPlusKeyboardProfiles", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("ArtemisPlusKeyCombos", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("ArtemisPlusActionButtons", Context.MODE_PRIVATE)
                .edit().clear().commit();
        for (int i = 1; i <= 5; i++) {
            String storage = i == 1 ? "OSC_Keyboard" : "OSC_Keyboard_" + i;
            context.getSharedPreferences(storage, Context.MODE_PRIVATE).edit().clear().commit();
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, "OSC_Keyboard")
                .commit();
    }

    @Test
    public void legacySelectedLayoutMigratesIntoNormalProfile() {
        context.getSharedPreferences("OSC_Keyboard", Context.MODE_PRIVATE)
                .edit().putString("key_111", "geometry").commit();

        List<KeyboardProfilesManager.Profile> profiles = KeyboardProfilesManager.getProfiles(context);
        KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(context);

        assertEquals(1, profiles.size());
        assertEquals("OSC_Keyboard", active.storageName);
        assertEquals(active.storageName,
                PreferenceManager.getDefaultSharedPreferences(context).getString(
                        KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, null));
    }

    @Test
    public void createRenameDuplicateReorderAndDeleteKeepsValidActiveProfile() {
        KeyboardProfilesManager.Profile first = KeyboardProfilesManager.getActiveProfile(context);
        context.getSharedPreferences(first.storageName, Context.MODE_PRIVATE)
                .edit().putString("example", "value").commit();

        KeyboardProfilesManager.Profile second = KeyboardProfilesManager.createProfile(context, "Work");
        assertTrue(KeyboardProfilesManager.renameProfile(context, second.id, "Gaming"));
        assertTrue(KeyboardProfilesManager.setActiveProfile(context, second.id));
        assertEquals(second.id, KeyboardProfilesManager.getActiveProfile(context).id);

        KeyboardProfilesManager.Profile duplicate = KeyboardProfilesManager.duplicateProfile(context, first.id);
        assertNotNull(duplicate);
        assertNotEquals(first.storageName, duplicate.storageName);
        assertEquals("value", context.getSharedPreferences(duplicate.storageName, Context.MODE_PRIVATE)
                .getString("example", null));

        assertTrue(KeyboardProfilesManager.moveProfile(context, duplicate.id, 1));
        assertTrue(KeyboardProfilesManager.deleteProfile(context, second.id));
        assertNotEquals(second.id, KeyboardProfilesManager.getActiveProfile(context).id);
        assertTrue(KeyboardProfilesManager.getProfiles(context).size() >= 2);
    }

    @Test
    public void lastProfileCannotBeDeleted() {
        KeyboardProfilesManager.Profile only = KeyboardProfilesManager.getActiveProfile(context);
        assertFalse(KeyboardProfilesManager.deleteProfile(context, only.id));
        assertEquals(only.id, KeyboardProfilesManager.getActiveProfile(context).id);
    }

    @Test
    public void legacyImportAddsProfileWithoutReplacingExistingOrChangingActive() throws Exception {
        KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(context);
        int before = KeyboardProfilesManager.getProfiles(context).size();

        JSONObject legacy = new JSONObject();
        legacy.put("key_111", "{\"LEFT\":12}");
        int imported = KeyboardProfilesManager.importProfiles(context, legacy.toString());

        assertEquals(1, imported);
        assertEquals(before + 1, KeyboardProfilesManager.getProfiles(context).size());
        assertEquals(active.id, KeyboardProfilesManager.getActiveProfile(context).id);
    }

    @Test
    public void bundleExportContainsAllProfiles() throws Exception {
        KeyboardProfilesManager.getActiveProfile(context);
        KeyboardProfilesManager.createProfile(context, "Second");
        JSONObject exported = KeyboardProfilesManager.exportProfiles(context);

        assertEquals("artemis-plus-keyboard-profiles", exported.getString("format"));
        assertEquals(2, exported.getJSONArray("profiles").length());
    }
}
