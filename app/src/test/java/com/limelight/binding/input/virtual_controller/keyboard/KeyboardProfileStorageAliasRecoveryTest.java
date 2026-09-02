package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KeyboardProfileStorageAliasRecoveryTest {
    private Context context;
    private SharedPreferences metadata;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        metadata = context.getSharedPreferences("ArtemisPlusKeyboardProfiles", Context.MODE_PRIVATE);
        metadata.edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void duplicateStorageMetadataKeepsOnlyFirstProfileWithoutClearingBackingData()
            throws Exception {
        String storage = "ArtemisKeyboardProfile_shared";
        context.getSharedPreferences(storage, Context.MODE_PRIVATE)
                .edit().clear().putString("key_111", "geometry").commit();

        JSONArray profilesJson = new JSONArray()
                .put(new JSONObject()
                        .put("id", "first")
                        .put("name", "First")
                        .put("storage", storage))
                .put(new JSONObject()
                        .put("id", "second")
                        .put("name", "Second")
                        .put("storage", storage));
        metadata.edit()
                .putString("profiles_v1", profilesJson.toString())
                .putString("active_profile_id", "second")
                .commit();

        List<KeyboardProfilesManager.Profile> profiles = KeyboardProfilesManager.getProfiles(context);
        KeyboardProfilesManager.Profile active = KeyboardProfilesManager.getActiveProfile(context);

        assertEquals(1, profiles.size());
        assertEquals("first", profiles.get(0).id);
        assertEquals(storage, profiles.get(0).storageName);
        assertEquals("first", active.id);
        assertEquals("geometry", context.getSharedPreferences(storage, Context.MODE_PRIVATE)
                .getString("key_111", null));
        assertEquals(1, new JSONArray(metadata.getString("profiles_v1", "[]")).length());
    }
}
