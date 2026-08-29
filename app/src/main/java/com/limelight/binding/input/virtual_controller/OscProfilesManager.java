package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists OSC profile metadata while keeping the actual control geometry in the existing
 * SharedPreferences format. The built-in "Default" profile deliberately uses the legacy
 * "OSC" preference file so existing Artemis layouts continue to work without migration.
 */
public final class OscProfilesManager {
    private static final String META_PREFERENCES = "ArtemisPlusOscProfiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_GAME_PROFILE_PREFIX = "game_profile_";
    private static final String PROFILE_PREFERENCE_PREFIX = "OSC_PROFILE_";

    private OscProfilesManager() {
    }

    public static synchronized List<OscProfile> getProfiles(Context context) {
        ArrayList<OscProfile> profiles = readProfiles(context);
        ensureDefaultProfile(profiles);
        return profiles;
    }

    public static synchronized OscProfile getActiveProfile(Context context) {
        List<OscProfile> profiles = getProfiles(context);
        String activeId = getMetaPreferences(context).getString(
                KEY_ACTIVE_PROFILE,
                OscProfile.DEFAULT_ID);

        OscProfile profile = findById(profiles, activeId);
        return profile != null ? profile : findById(profiles, OscProfile.DEFAULT_ID);
    }

    public static synchronized String getActiveProfileId(Context context) {
        OscProfile profile = getActiveProfile(context);
        return profile != null ? profile.getId() : OscProfile.DEFAULT_ID;
    }

    public static synchronized boolean setActiveProfile(Context context, String profileId) {
        if (findById(getProfiles(context), profileId) == null) {
            return false;
        }

        getMetaPreferences(context)
                .edit()
                .putString(KEY_ACTIVE_PROFILE, profileId)
                .apply();
        return true;
    }

    public static synchronized OscProfile createProfile(Context context, String requestedName) {
        ArrayList<OscProfile> profiles = new ArrayList<>(getProfiles(context));
        String name = normalizeName(requestedName, "OSC Profile " + profiles.size());
        OscProfile profile = new OscProfile(UUID.randomUUID().toString(), name);
        profiles.add(profile);
        writeProfiles(context, profiles);
        return profile;
    }

    public static synchronized boolean renameProfile(Context context,
                                                     String profileId,
                                                     String requestedName) {
        ArrayList<OscProfile> profiles = new ArrayList<>(getProfiles(context));
        OscProfile profile = findById(profiles, profileId);
        if (profile == null) {
            return false;
        }

        profile.setName(normalizeName(requestedName, profile.getName()));
        writeProfiles(context, profiles);
        return true;
    }

    public static synchronized boolean deleteProfile(Context context, String profileId) {
        if (profileId == null || OscProfile.DEFAULT_ID.equals(profileId)) {
            return false;
        }

        ArrayList<OscProfile> profiles = new ArrayList<>(getProfiles(context));
        OscProfile profile = findById(profiles, profileId);
        if (profile == null) {
            return false;
        }

        profiles.remove(profile);
        SharedPreferences meta = getMetaPreferences(context);
        SharedPreferences.Editor metaEditor = meta.edit();

        if (profileId.equals(meta.getString(KEY_ACTIVE_PROFILE, OscProfile.DEFAULT_ID))) {
            metaEditor.putString(KEY_ACTIVE_PROFILE, OscProfile.DEFAULT_ID);
        }

        // Remove per-game mappings that pointed at the deleted profile.
        for (Map.Entry<String, ?> entry : meta.getAll().entrySet()) {
            if (entry.getKey().startsWith(KEY_GAME_PROFILE_PREFIX)
                    && profileId.equals(entry.getValue())) {
                metaEditor.remove(entry.getKey());
            }
        }

        metaEditor.apply();
        writeProfiles(context, profiles);
        context.getSharedPreferences(getPreferenceNameForProfile(profileId), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        return true;
    }

    /**
     * Returns the preference file used by VirtualControllerConfigurationLoader for the currently
     * active profile. The Default profile maps to the old Artemis "OSC" file for compatibility.
     */
    public static synchronized String getActivePreferenceName(Context context) {
        return getPreferenceNameForProfile(getActiveProfileId(context));
    }

    public static String getPreferenceNameForProfile(String profileId) {
        if (profileId == null || OscProfile.DEFAULT_ID.equals(profileId)) {
            return VirtualControllerConfigurationLoader.OSC_PREFERENCE;
        }
        return PROFILE_PREFERENCE_PREFIX + profileId;
    }

    public static synchronized boolean setProfileForGame(Context context,
                                                         String gameKey,
                                                         String profileId) {
        if (gameKey == null || gameKey.trim().isEmpty()) {
            return false;
        }
        if (findById(getProfiles(context), profileId) == null) {
            return false;
        }

        getMetaPreferences(context)
                .edit()
                .putString(KEY_GAME_PROFILE_PREFIX + gameKey, profileId)
                .apply();
        return true;
    }

    public static synchronized String getProfileForGame(Context context, String gameKey) {
        if (gameKey == null || gameKey.trim().isEmpty()) {
            return null;
        }
        String profileId = getMetaPreferences(context).getString(
                KEY_GAME_PROFILE_PREFIX + gameKey,
                null);
        return findById(getProfiles(context), profileId) != null ? profileId : null;
    }

    public static synchronized boolean activateProfileForGame(Context context, String gameKey) {
        String profileId = getProfileForGame(context, gameKey);
        return profileId != null && setActiveProfile(context, profileId);
    }

    private static SharedPreferences getMetaPreferences(Context context) {
        return context.getSharedPreferences(META_PREFERENCES, Context.MODE_PRIVATE);
    }

    private static ArrayList<OscProfile> readProfiles(Context context) {
        ArrayList<OscProfile> profiles = new ArrayList<>();
        String serialized = getMetaPreferences(context).getString(KEY_PROFILES, null);
        if (serialized == null || serialized.trim().isEmpty()) {
            profiles.add(defaultProfile());
            writeProfiles(context, profiles);
            return profiles;
        }

        try {
            JSONArray array = new JSONArray(serialized);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    OscProfile profile = OscProfile.fromJson(object);
                    if (findById(profiles, profile.getId()) == null) {
                        profiles.add(profile);
                    }
                }
            }
        } catch (JSONException ignored) {
            profiles.clear();
        }

        if (profiles.isEmpty()) {
            profiles.add(defaultProfile());
            writeProfiles(context, profiles);
        }
        return profiles;
    }

    private static void ensureDefaultProfile(List<OscProfile> profiles) {
        if (findById(profiles, OscProfile.DEFAULT_ID) == null) {
            profiles.add(0, defaultProfile());
        }
    }

    private static void writeProfiles(Context context, List<OscProfile> profiles) {
        JSONArray array = new JSONArray();
        for (OscProfile profile : profiles) {
            try {
                array.put(profile.toJson());
            } catch (JSONException ignored) {
                // A single bad metadata entry should not make all profiles unusable.
            }
        }
        getMetaPreferences(context).edit().putString(KEY_PROFILES, array.toString()).apply();
    }

    private static OscProfile defaultProfile() {
        return new OscProfile(OscProfile.DEFAULT_ID, "Default");
    }

    private static OscProfile findById(List<OscProfile> profiles, String profileId) {
        if (profileId == null) {
            return null;
        }
        for (OscProfile profile : profiles) {
            if (profileId.equals(profile.getId())) {
                return profile;
            }
        }
        return null;
    }

    private static String normalizeName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }
}
