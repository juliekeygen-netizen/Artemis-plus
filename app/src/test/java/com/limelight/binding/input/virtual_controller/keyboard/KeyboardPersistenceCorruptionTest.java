package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
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

import java.util.List;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class KeyboardPersistenceCorruptionTest {
    private static final String PROFILE_META = "ArtemisPlusKeyboardProfiles";
    private static final String KEY_PROFILES = "profiles_v1";
    private static final String KEY_ACTIVE = "active_profile_id";
    private static final String COMBO_PREFS = "ArtemisPlusKeyCombos";
    private static final String ACTION_PREFS = "ArtemisPlusActionButtons";

    private Context context;

    @Before
    public void resetPreferences() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(PROFILE_META, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(COMBO_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(ACTION_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE)
                .commit();
        for (String storage : new String[]{
                "OSC_Keyboard", "OSC_Keyboard_2", "OSC_Keyboard_3", "OSC_Keyboard_4", "OSC_Keyboard_5"}) {
            context.getSharedPreferences(storage, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @Test
    public void keyboardProfilesKeepValidSiblingsAndRepairWrongTypedActiveId() throws Exception {
        JSONArray stored = new JSONArray();
        stored.put(profile("keep-a", "Keep A", "OSC_Keyboard"));
        stored.put(new JSONObject().put("id", "broken").put("name", "Broken"));
        stored.put(profile("keep-b", "Keep B", "OSC_Keyboard_2"));

        SharedPreferences meta = context.getSharedPreferences(PROFILE_META, Context.MODE_PRIVATE);
        meta.edit()
                .putString(KEY_PROFILES, stored.toString())
                .putInt(KEY_ACTIVE, 42)
                .commit();

        List<KeyboardProfilesManager.Profile> profiles = KeyboardProfilesManager.getProfiles(context);
        assertEquals(2, profiles.size());
        assertEquals("keep-a", profiles.get(0).id);
        assertEquals("keep-b", profiles.get(1).id);
        assertEquals("keep-a", KeyboardProfilesManager.getActiveProfile(context).id);

        JSONArray repaired = new JSONArray(meta.getString(KEY_PROFILES, "[]"));
        assertEquals(2, repaired.length());
        assertEquals("keep-a", meta.getString(KEY_ACTIVE, ""));
    }

    @Test
    public void wrongTypedLegacyLayoutFallsBackWithoutCrashing() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, 7)
                .commit();

        KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(context);

        assertEquals(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE, active.storageName);
        assertEquals(active.storageName,
                PreferenceManager.getDefaultSharedPreferences(context).getString(
                        KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, ""));
    }

    @Test
    public void keyCombosKeepValidSiblingsAroundMalformedEntry() throws Exception {
        String layout = "corrupt-layout";
        JSONArray stored = new JSONArray();
        stored.put(new KeyComboManager.Definition(
                "first", "First", new int[0], new int[]{KeyEvent.KEYCODE_F1}).toJson());
        stored.put("not-an-object");
        stored.put(new KeyComboManager.Definition(
                "second", "Second", new int[]{KeyEvent.KEYCODE_CTRL_LEFT},
                new int[]{KeyEvent.KEYCODE_F2}).toJson());

        SharedPreferences prefs = context.getSharedPreferences(COMBO_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString("definitions_" + layout, stored.toString()).commit();

        List<KeyComboManager.Definition> definitions = KeyComboManager.loadDefinitionsForLayout(context, layout);
        assertEquals(2, definitions.size());
        assertEquals("first", definitions.get(0).id);
        assertEquals("second", definitions.get(1).id);
        assertEquals(2, new JSONArray(prefs.getString("definitions_" + layout, "[]")).length());
    }

    @Test
    public void wrongTypedComboStorageFallsBackAndCopiesAsEmptyArray() {
        String source = "wrong-type";
        String target = "copied";
        SharedPreferences prefs = context.getSharedPreferences(COMBO_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt("definitions_" + source, 99).commit();

        assertTrue(KeyComboManager.loadDefinitionsForLayout(context, source).isEmpty());
        assertEquals(0, KeyComboManager.exportDefinitionsForLayout(context, source).length());

        KeyComboManager.copyDefinitionsForLayout(context, source, target);
        assertEquals("[]", prefs.getString("definitions_" + target, "missing"));
    }

    @Test
    public void wrongTypedActionSelectionIsInertAndFutureIdsStillRoundTrip() {
        String layout = "actions-layout";
        SharedPreferences prefs = context.getSharedPreferences(ACTION_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt("selected_actions_" + layout, 11).commit();

        assertEquals(0, ArtemisActionButtonFactory.exportSelectionForLayout(context, layout).length());

        JSONArray imported = new JSONArray();
        imported.put(ArtemisAction.QUICK_MENU.getId());
        imported.put("future.action.from.newer.client");
        ArtemisActionButtonFactory.importSelectionForLayout(context, layout, imported);

        JSONArray exported = ArtemisActionButtonFactory.exportSelectionForLayout(context, layout);
        assertEquals(2, exported.length());
        assertEquals(ArtemisAction.QUICK_MENU.getId(), exported.optString(0));
        assertEquals("future.action.from.newer.client", exported.optString(1));
    }

    private static JSONObject profile(String id, String name, String storage) throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("storage", storage);
    }
}
