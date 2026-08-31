package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.ArtemisAction;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Test
    public void bundleRoundTripKeepsLayoutKeysAndActionsScopedToImportedProfile() throws Exception {
        KeyboardProfilesManager.Profile original = KeyboardProfilesManager.getActiveProfile(context);
        assertTrue(KeyboardProfilesManager.renameProfile(context, original.id, "Original"));
        KeyboardProfilesManager.Profile source = KeyboardProfilesManager.createProfile(context, "Travel");

        String sourceGeometry = "{\"LEFT\":12,\"TOP\":34}";
        context.getSharedPreferences(source.storageName, Context.MODE_PRIVATE)
                .edit().putString("key_111", sourceGeometry).commit();
        KeyComboManager.Definition sourceKey = new KeyComboManager.Definition(
                "travel-key", "Travel key", new int[]{KeyEvent.KEYCODE_ALT_LEFT},
                new int[]{KeyEvent.KEYCODE_F5});
        KeyComboManager.importDefinitionsForLayout(context, source.storageName,
                new JSONArray().put(sourceKey.toJson()));
        ArtemisActionButtonFactory.importSelectionForLayout(context, source.storageName,
                new JSONArray()
                        .put(ArtemisAction.SOFT_KEYBOARD.getId())
                        .put(ArtemisAction.QUICK_MENU.getId()));
        assertTrue(KeyboardProfilesManager.setActiveProfile(context, source.id));

        JSONObject bundle = KeyboardProfilesManager.exportProfiles(context);

        // Simulate importing onto a device that already has its own active profile.
        context.getSharedPreferences("ArtemisPlusKeyboardProfiles", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("ArtemisPlusKeyCombos", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("ArtemisPlusActionButtons", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(source.storageName, Context.MODE_PRIVATE)
                .edit().clear().commit();

        KeyboardProfilesManager.Profile existing = KeyboardProfilesManager.getActiveProfile(context);
        ArtemisActionButtonFactory.importSelectionForLayout(context, existing.storageName,
                new JSONArray().put(ArtemisAction.TOGGLE_HUD.getId()));
        int before = KeyboardProfilesManager.getProfiles(context).size();

        assertEquals(2, KeyboardProfilesManager.importProfiles(context, bundle.toString()));
        assertEquals(before + 2, KeyboardProfilesManager.getProfiles(context).size());
        assertEquals(existing.id, KeyboardProfilesManager.getActiveProfile(context).id);

        KeyboardProfilesManager.Profile imported = profileNamed("Travel");
        assertNotNull(imported);
        assertEquals(sourceGeometry, context.getSharedPreferences(imported.storageName, Context.MODE_PRIVATE)
                .getString("key_111", null));

        JSONArray importedKeys = KeyComboManager.exportDefinitionsForLayout(context, imported.storageName);
        assertEquals(1, importedKeys.length());
        KeyComboManager.Definition restoredKey = KeyComboManager.Definition.fromJson(
                importedKeys.getJSONObject(0));
        assertEquals("travel-key", restoredKey.id);
        assertEquals(KeyEvent.KEYCODE_F5, restoredKey.keys[0]);
        assertEquals(KeyEvent.KEYCODE_ALT_LEFT, restoredKey.modifiers[0]);

        assertEquals(actionIds(new JSONArray()
                        .put(ArtemisAction.SOFT_KEYBOARD.getId())
                        .put(ArtemisAction.QUICK_MENU.getId())),
                actionIds(ArtemisActionButtonFactory.exportSelectionForLayout(context,
                        imported.storageName)));
        assertEquals(Collections.singleton(ArtemisAction.TOGGLE_HUD.getId()),
                actionIds(ArtemisActionButtonFactory.exportSelectionForLayout(context,
                        existing.storageName)));
    }

    @Test
    public void bundleImportPreservesUnknownActionIdsForForwardCompatibility() throws Exception {
        String futureId = "future.action.from.newer.client";
        JSONObject profile = new JSONObject()
                .put("name", "Future actions")
                .put("layout", new JSONObject().put("key_111", "{\"LEFT\":4}"))
                .put("actions", new JSONArray()
                        .put(futureId)
                        .put(ArtemisAction.QUICK_MENU.getId())
                        .put(ArtemisAction.QUICK_MENU.getId()));
        JSONObject bundle = new JSONObject()
                .put("format", "artemis-plus-keyboard-profiles")
                .put("version", 1)
                .put("profiles", new JSONArray().put(profile));

        assertEquals(1, KeyboardProfilesManager.importProfiles(context, bundle.toString()));
        KeyboardProfilesManager.Profile imported = profileNamed("Future actions");
        assertNotNull(imported);

        JSONArray exported = ArtemisActionButtonFactory.exportSelectionForLayout(
                context, imported.storageName);
        assertEquals(2, exported.length());
        assertEquals(ArtemisAction.QUICK_MENU.getId(), exported.getString(0));
        assertEquals(futureId, exported.getString(1));
        Set<String> stored = context.getSharedPreferences("ArtemisPlusActionButtons", Context.MODE_PRIVATE)
                .getStringSet("selected_actions_" + imported.storageName, Collections.emptySet());
        assertTrue(stored.contains(futureId));
    }

    @Test
    public void unsupportedBundleMetadataIsRejectedWithoutMutation() throws Exception {
        JSONObject profile = new JSONObject()
                .put("name", "Should not import")
                .put("layout", new JSONObject().put("key_111", "geometry"));

        assertImportRejectedWithoutMutation(new JSONObject()
                .put("format", "some-other-profile-format")
                .put("version", 1)
                .put("profiles", new JSONArray().put(profile)));

        assertImportRejectedWithoutMutation(new JSONObject()
                .put("format", "artemis-plus-keyboard-profiles")
                .put("version", 2)
                .put("profiles", new JSONArray().put(profile)));
    }

    @Test
    public void malformedLaterProfileIsRejectedBeforeAnyImportWrites() throws Exception {
        JSONObject valid = new JSONObject()
                .put("name", "First but invalid transaction")
                .put("layout", new JSONObject().put("key_111", "geometry"));
        JSONObject bundle = new JSONObject()
                .put("format", "artemis-plus-keyboard-profiles")
                .put("version", 1)
                .put("profiles", new JSONArray().put(valid).put("not-an-object"));

        assertImportRejectedWithoutMutation(bundle);
        assertEquals(null, profileNamed("First but invalid transaction"));
    }

    private void assertImportRejectedWithoutMutation(JSONObject bundle) throws Exception {
        KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(context);
        int before = KeyboardProfilesManager.getProfiles(context).size();
        boolean rejected = false;
        try {
            KeyboardProfilesManager.importProfiles(context, bundle.toString());
        } catch (Exception expected) {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals(before, KeyboardProfilesManager.getProfiles(context).size());
        assertEquals(active.id, KeyboardProfilesManager.getActiveProfile(context).id);
    }

    private KeyboardProfilesManager.Profile profileNamed(String name) {
        for (KeyboardProfilesManager.Profile profile : KeyboardProfilesManager.getProfiles(context)) {
            if (name.equals(profile.name)) {
                return profile;
            }
        }
        return null;
    }

    private static Set<String> actionIds(JSONArray array) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            ids.add(array.optString(i));
        }
        return ids;
    }
}
