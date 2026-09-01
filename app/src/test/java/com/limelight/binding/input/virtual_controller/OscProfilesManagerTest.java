package com.limelight.binding.input.virtual_controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    private static final String KEY_GAME_PROFILE_PREFIX = "game_profile_";
    private static final String KEY_SNAPSHOT_INITIALIZED_PREFIX = "snapshot_initialized_";

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
    public void malformedTopLevelMetadataRecoversReferencedProfilesDeterministically() throws Exception {
        SharedPreferences meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        String gameKey = "recovered-game";
        meta.edit()
                .putString(KEY_PROFILES, "{definitely-not-a-profile-array")
                .putString(KEY_ACTIVE_PROFILE, "z-active")
                .putString(KEY_GAME_PROFILE_PREFIX + gameKey, "a-mapped")
                .putBoolean(KEY_SNAPSHOT_INITIALIZED_PREFIX + "m-snapshot", true)
                .putBoolean(KEY_SNAPSHOT_INITIALIZED_PREFIX + "ignored-false", false)
                .putBoolean(KEY_SNAPSHOT_INITIALIZED_PREFIX + "a-mapped", true)
                .commit();

        List<OscProfile> profiles = OscProfilesManager.getProfiles(context);

        assertEquals(4, profiles.size());
        assertEquals(OscProfile.DEFAULT_ID, profiles.get(0).getId());
        assertEquals("a-mapped", profiles.get(1).getId());
        assertEquals("Recovered OSC Profile 1", profiles.get(1).getName());
        assertEquals("m-snapshot", profiles.get(2).getId());
        assertEquals("Recovered OSC Profile 2", profiles.get(2).getName());
        assertEquals("z-active", profiles.get(3).getId());
        assertEquals("Recovered OSC Profile 3", profiles.get(3).getName());
        assertEquals("z-active", OscProfilesManager.getActiveProfileId(context));
        assertEquals("a-mapped", OscProfilesManager.getProfileForGame(context, gameKey));

        JSONArray persisted = new JSONArray(meta.getString(KEY_PROFILES, "[]"));
        assertEquals(4, persisted.length());
    }

    @Test
    public void malformedProfileEntryDoesNotDiscardValidSiblings() throws Exception {
        SharedPreferences meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        JSONArray profiles = new JSONArray()
                .put(new JSONObject().put("id", OscProfile.DEFAULT_ID).put("name", "Default"))
                .put(new JSONObject().put("name", "Missing id"))
                .put(new JSONObject().put("id", "still-valid").put("name", "Still valid"));
        meta.edit().putString(KEY_PROFILES, profiles.toString()).commit();

        List<OscProfile> repaired = OscProfilesManager.getProfiles(context);

        assertEquals(2, repaired.size());
        assertTrue(containsId(repaired, OscProfile.DEFAULT_ID));
        assertTrue(containsId(repaired, "still-valid"));
        JSONArray persisted = new JSONArray(meta.getString(KEY_PROFILES, "[]"));
        assertEquals(2, persisted.length());
    }

    @Test
    public void wrongTypeMetadataIsRepairedWithoutClassCastCrashes() {
        SharedPreferences meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        String gameKey = "wrong-type-game";
        meta.edit()
                .putInt(KEY_PROFILES, 7)
                .putInt(KEY_ACTIVE_PROFILE, 8)
                .putInt(KEY_GAME_PROFILE_PREFIX + gameKey, 9)
                .putString(KEY_SNAPSHOT_INITIALIZED_PREFIX + "not-a-boolean", "true")
                .commit();

        List<OscProfile> profiles = OscProfilesManager.getProfiles(context);
        assertEquals(1, profiles.size());
        assertEquals(OscProfile.DEFAULT_ID, profiles.get(0).getId());
        assertTrue(meta.getAll().get(KEY_PROFILES) instanceof String);

        assertEquals(OscProfile.DEFAULT_ID, OscProfilesManager.getActiveProfileId(context));
        assertEquals(OscProfile.DEFAULT_ID, meta.getString(KEY_ACTIVE_PROFILE, null));

        assertNull(OscProfilesManager.getProfileForGame(context, gameKey));
        assertFalse(meta.contains(KEY_GAME_PROFILE_PREFIX + gameKey));
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
    public void blankAndOversizedProfileNamesAreNormalized() {
        OscProfile created = OscProfilesManager.createProfile(context, "   ");
        assertEquals("OSC Profile 1", created.getName());

        String oversized = repeat('x', 100);
        assertTrue(OscProfilesManager.renameProfile(context, created.getId(), oversized));
        OscProfile renamed = findById(OscProfilesManager.getProfiles(context), created.getId());
        assertNotNull(renamed);
        assertEquals(80, renamed.getName().length());
        assertEquals(oversized.substring(0, 80), renamed.getName());
    }

    @Test
    public void defaultProfileCannotBeDeleted() {
        assertFalse(OscProfilesManager.deleteProfile(context, OscProfile.DEFAULT_ID));
        assertTrue(containsId(OscProfilesManager.getProfiles(context), OscProfile.DEFAULT_ID));
    }

    @Test
    public void perGameMappingCanBeSetChangedAndCleared() {
        OscProfile first = OscProfilesManager.createProfile(context, "First");
        OscProfile second = OscProfilesManager.createProfile(context, "Second");
        String gameKey = "v1|pc=test|app=uuid=test-game";

        assertTrue(OscProfilesManager.setProfileForGame(context, gameKey, first.getId()));
        assertEquals(first.getId(), OscProfilesManager.getProfileForGame(context, gameKey));

        assertTrue(OscProfilesManager.setProfileForGame(context, gameKey, second.getId()));
        assertEquals(second.getId(), OscProfilesManager.getProfileForGame(context, gameKey));

        assertTrue(OscProfilesManager.clearProfileForGame(context, gameKey));
        assertNull(OscProfilesManager.getProfileForGame(context, gameKey));
    }

    @Test
    public void deletingProfileRemovesPerGameMappings() {
        OscProfile mapped = OscProfilesManager.createProfile(context, "Mapped");
        String firstGame = "game-one";
        String secondGame = "game-two";
        assertTrue(OscProfilesManager.setProfileForGame(context, firstGame, mapped.getId()));
        assertTrue(OscProfilesManager.setProfileForGame(context, secondGame, mapped.getId()));

        assertTrue(OscProfilesManager.deleteProfile(context, mapped.getId()));

        assertNull(OscProfilesManager.getProfileForGame(context, firstGame));
        assertNull(OscProfilesManager.getProfileForGame(context, secondGame));
    }

    @Test
    public void stalePerGameMappingIsRepairedOnRead() {
        SharedPreferences meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
        String gameKey = "stale-game";
        String preferenceKey = KEY_GAME_PROFILE_PREFIX + gameKey;
        meta.edit().putString(preferenceKey, "missing-profile").commit();

        assertNull(OscProfilesManager.getProfileForGame(context, gameKey));
        assertFalse(meta.contains(preferenceKey));
        assertFalse(containsId(OscProfilesManager.getProfiles(context), "missing-profile"));
    }

    @Test
    public void gameProfileKeyIsHostScopedAndPrefersStableAppUuid() {
        String firstHost = OscGameProfileKey.build(
                "pc-one", "10.0.0.2", "app-uuid", 5);
        String sameHostDifferentAppId = OscGameProfileKey.build(
                "pc-one", "10.0.0.2", "app-uuid", 999);
        String secondHost = OscGameProfileKey.build(
                "pc-two", "10.0.0.2", "app-uuid", 5);

        assertEquals(firstHost, sameHostDifferentAppId);
        assertFalse(firstHost.equals(secondHost));
        assertTrue(firstHost.contains("uuid=app-uuid"));
    }

    @Test
    public void gameProfileKeyFallsBackToHostAndAppId() {
        String key = OscGameProfileKey.build(null, "192.168.1.20", null, 42);
        assertNotNull(key);
        assertTrue(key.contains("pc=192.168.1.20"));
        assertTrue(key.contains("app=id=42"));
        assertNull(OscGameProfileKey.build(null, " ", null, -1));
    }

    @Test
    public void gameProfileKeyEscapesPreferenceDelimiters() {
        String key = OscGameProfileKey.build("pc|one", null, "app=one%two", 1);
        assertNotNull(key);
        assertTrue(key.contains("pc=pc%7Cone"));
        assertTrue(key.contains("uuid=app%3Done%25two"));
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

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
