package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@Config(sdk = {33})
@RunWith(RobolectricTestRunner.class)
public class OscProfilesManagerTest {
    private static final String META_PREFS = "ArtemisPlusOscProfiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE,
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void missingDefaultProfileIsRepairedAndPersisted() throws JSONException {
        SharedPreferences meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        JSONArray brokenProfiles = new JSONArray();
        brokenProfiles.put(new JSONObject()
                .put("id", "custom-only")
                .put("name", "Custom only"));
        meta.edit().putString(KEY_PROFILES, brokenProfiles.toString()).commit();

        List<OscProfile> profiles = OscProfilesManager.getProfiles(context);

        assertTrue(containsId(profiles, OscProfile.DEFAULT_ID));
        assertTrue(containsId(profiles, "custom-only"));

        JSONArray persisted = new JSONArray(meta.getString(KEY_PROFILES, "[]"));
        boolean persistedDefault = false;
        for (int i = 0; i < persisted.length(); i++) {
            if (OscProfile.DEFAULT_ID.equals(persisted.getJSONObject(i).optString("id"))) {
                persistedDefault = true;
                break;
            }
        }
        assertTrue("Default profile repair must be written back to SharedPreferences",
                persistedDefault);
    }

    @Test
    public void invalidActiveProfileFallsBackAndRepairsMetadata() {
        SharedPreferences meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        meta.edit().putString(KEY_ACTIVE_PROFILE, "does-not-exist").commit();

        OscProfile active = OscProfilesManager.getActiveProfile(context);

        assertNotNull(active);
        assertEquals(OscProfile.DEFAULT_ID, active.getId());
        assertEquals(OscProfile.DEFAULT_ID,
                meta.getString(KEY_ACTIVE_PROFILE, null));
    }

    @Test
    public void createRenameActivateAndDeleteProfileKeepsValidState() {
        OscProfile created = OscProfilesManager.createProfile(context, "Test profile");
        assertNotNull(created);
        assertTrue(containsId(OscProfilesManager.getProfiles(context), created.getId()));

        assertTrue(OscProfilesManager.renameProfile(context, created.getId(), "Renamed"));
        OscProfile renamed = findById(OscProfilesManager.getProfiles(context), created.getId());
        assertNotNull(renamed);
        assertEquals("Renamed", renamed.getName());

        assertTrue(OscProfilesManager.setActiveProfile(context, created.getId()));
        assertEquals(created.getId(), OscProfilesManager.getActiveProfileId(context));

        assertTrue(OscProfilesManager.deleteProfile(context, created.getId()));
        assertFalse(containsId(OscProfilesManager.getProfiles(context), created.getId()));
        assertEquals(OscProfile.DEFAULT_ID, OscProfilesManager.getActiveProfileId(context));
    }

    @Test
    public void defaultProfileCannotBeDeleted() {
        assertFalse(OscProfilesManager.deleteProfile(context, OscProfile.DEFAULT_ID));
        assertTrue(containsId(OscProfilesManager.getProfiles(context), OscProfile.DEFAULT_ID));
    }

    private static boolean containsId(List<OscProfile> profiles, String id) {
        return findById(profiles, id) != null;
    }

    private static OscProfile findById(List<OscProfile> profiles, String id) {
        for (OscProfile profile : profiles) {
            if (id.equals(profile.getId())) {
                return profile;
            }
        }
        return null;
    }
}
