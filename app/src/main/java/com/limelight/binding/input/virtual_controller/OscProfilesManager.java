package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Persistent OSC profile manager.
 *
 * <p>The existing Artemis controller loader always reads and writes the legacy "OSC"
 * SharedPreferences file. Rather than rewriting that mature loader, Artemis Plus treats it as a
 * working set: when switching profiles the working set is snapshotted, the selected profile is
 * restored into it, and the VirtualController refreshes. This keeps old user layouts compatible
 * while allowing unlimited independent profiles.</p>
 */
public final class OscProfilesManager {
    private static final String META_PREFERENCES = "ArtemisPlusOscProfiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_GAME_PROFILE_PREFIX = "game_profile_";
    private static final String KEY_SNAPSHOT_INITIALIZED_PREFIX = "snapshot_initialized_";
    private static final String PROFILE_SNAPSHOT_PREFIX = "OSC_PROFILE_";
    private static final int MAX_RECOVERED_ID_LENGTH = 200;

    private OscProfilesManager() {
    }

    public static synchronized List<OscProfile> getProfiles(Context context) {
        ArrayList<OscProfile> profiles = readProfiles(context);
        if (ensureDefaultProfile(profiles)) {
            // Repair metadata immediately rather than only fixing the in-memory result.
            writeProfiles(context, profiles);
        }
        return profiles;
    }

    public static synchronized OscProfile getActiveProfile(Context context) {
        List<OscProfile> profiles = getProfiles(context);
        SharedPreferences meta = getMetaPreferences(context);
        Object rawActive = meta.getAll().get(KEY_ACTIVE_PROFILE);
        String activeId = rawActive instanceof String
                ? (String) rawActive
                : OscProfile.DEFAULT_ID;

        OscProfile profile = findById(profiles, activeId);
        if (profile != null) {
            // Wrong-type active metadata should not survive merely because Default is a valid
            // fallback. Normalize it so later readers never hit a ClassCastException.
            if (rawActive != null && !(rawActive instanceof String)) {
                meta.edit().putString(KEY_ACTIVE_PROFILE, profile.getId()).apply();
            }
            return profile;
        }

        // Recover cleanly if metadata references a profile that no longer exists.
        OscProfile fallback = findById(profiles, OscProfile.DEFAULT_ID);
        if (fallback != null) {
            meta.edit().putString(KEY_ACTIVE_PROFILE, OscProfile.DEFAULT_ID).apply();
        }
        return fallback;
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

    /**
     * Saves the current OSC working set, restores the requested profile, and immediately rebuilds
     * the visible controller. New profiles start from the normal Artemis default layout.
     */
    public static synchronized boolean switchProfile(Context context,
                                                     VirtualController controller,
                                                     String profileId) {
        if (controller == null || findById(getProfiles(context), profileId) == null) {
            return false;
        }

        String currentId = getActiveProfileId(context);
        if (profileId.equals(currentId)) {
            VirtualControllerConfigurationLoader.saveProfile(controller, context);
            return true;
        }

        VirtualController.ControllerMode previousMode = controller.getControllerMode();

        // Capture the currently displayed profile using the existing Artemis serialization.
        VirtualControllerConfigurationLoader.saveProfile(controller, context);
        snapshotWorkingSet(context, currentId);

        // Restore the target into the legacy working preference file. An untouched profile has no
        // snapshot yet, so clearing the working set makes refreshLayout() use stock defaults.
        restoreWorkingSet(context, profileId);
        if (!setActiveProfile(context, profileId)) {
            // This should be unreachable because the profile was validated above, but avoid
            // rebuilding into an inconsistent state if metadata changes unexpectedly.
            restoreWorkingSet(context, currentId);
            return false;
        }

        controller.refreshLayout();
        // A profile switch can happen while the user is in Move/Resize/Enable mode. Restore the
        // controller mode so disabled-button visibility and editing behaviour stay consistent.
        controller.setControllerMode(previousMode);
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

        Object rawActive = meta.getAll().get(KEY_ACTIVE_PROFILE);
        if (rawActive instanceof String && profileId.equals(rawActive)) {
            metaEditor.putString(KEY_ACTIVE_PROFILE, OscProfile.DEFAULT_ID);
        } else if (rawActive != null && !(rawActive instanceof String)) {
            metaEditor.putString(KEY_ACTIVE_PROFILE, OscProfile.DEFAULT_ID);
        }

        // Remove per-game mappings that pointed at the deleted profile.
        for (Map.Entry<String, ?> entry : meta.getAll().entrySet()) {
            if (entry.getKey().startsWith(KEY_GAME_PROFILE_PREFIX)
                    && profileId.equals(entry.getValue())) {
                metaEditor.remove(entry.getKey());
            }
        }

        metaEditor.remove(KEY_SNAPSHOT_INITIALIZED_PREFIX + profileId).apply();
        writeProfiles(context, profiles);
        context.getSharedPreferences(getSnapshotPreferenceName(profileId), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        return true;
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

    public static synchronized boolean clearProfileForGame(Context context, String gameKey) {
        if (gameKey == null || gameKey.trim().isEmpty()) {
            return false;
        }
        getMetaPreferences(context)
                .edit()
                .remove(KEY_GAME_PROFILE_PREFIX + gameKey)
                .apply();
        return true;
    }

    public static synchronized String getProfileForGame(Context context, String gameKey) {
        if (gameKey == null || gameKey.trim().isEmpty()) {
            return null;
        }
        SharedPreferences meta = getMetaPreferences(context);
        String preferenceKey = KEY_GAME_PROFILE_PREFIX + gameKey;
        Object rawProfileId = meta.getAll().get(preferenceKey);
        String profileId = rawProfileId instanceof String ? (String) rawProfileId : null;
        if (profileId != null && findById(getProfiles(context), profileId) != null) {
            return profileId;
        }

        // Repair stale/corrupt mappings immediately so they do not linger forever.
        if (rawProfileId != null) {
            meta.edit().remove(preferenceKey).apply();
        }
        return null;
    }

    public static synchronized boolean activateProfileForGame(Context context,
                                                              VirtualController controller,
                                                              String gameKey) {
        String profileId = getProfileForGame(context, gameKey);
        return profileId != null && switchProfile(context, controller, profileId);
    }

    private static void snapshotWorkingSet(Context context, String profileId) {
        SharedPreferences source = context.getSharedPreferences(
                VirtualControllerConfigurationLoader.OSC_PREFERENCE,
                Context.MODE_PRIVATE);
        SharedPreferences destination = context.getSharedPreferences(
                getSnapshotPreferenceName(profileId),
                Context.MODE_PRIVATE);
        copyPreferences(source, destination);
        getMetaPreferences(context)
                .edit()
                .putBoolean(KEY_SNAPSHOT_INITIALIZED_PREFIX + profileId, true)
                .apply();
    }

    private static void restoreWorkingSet(Context context, String profileId) {
        SharedPreferences working = context.getSharedPreferences(
                VirtualControllerConfigurationLoader.OSC_PREFERENCE,
                Context.MODE_PRIVATE);
        Object rawInitialized = getMetaPreferences(context).getAll().get(
                KEY_SNAPSHOT_INITIALIZED_PREFIX + profileId);
        boolean initialized = Boolean.TRUE.equals(rawInitialized);

        if (!initialized) {
            working.edit().clear().apply();
            return;
        }

        SharedPreferences snapshot = context.getSharedPreferences(
                getSnapshotPreferenceName(profileId),
                Context.MODE_PRIVATE);
        copyPreferences(snapshot, working);
    }

    private static void copyPreferences(SharedPreferences source, SharedPreferences destination) {
        SharedPreferences.Editor editor = destination.edit().clear();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Set) {
                Set<?> raw = (Set<?>) value;
                HashSet<String> strings = new HashSet<>();
                boolean allStrings = true;
                for (Object item : raw) {
                    if (!(item instanceof String)) {
                        allStrings = false;
                        break;
                    }
                    strings.add((String) item);
                }
                if (allStrings) {
                    editor.putStringSet(key, strings);
                }
            }
        }
        editor.apply();
    }

    private static String getSnapshotPreferenceName(String profileId) {
        return PROFILE_SNAPSHOT_PREFIX + (profileId == null ? OscProfile.DEFAULT_ID : profileId);
    }

    private static SharedPreferences getMetaPreferences(Context context) {
        return context.getSharedPreferences(META_PREFERENCES, Context.MODE_PRIVATE);
    }

    private static ArrayList<OscProfile> readProfiles(Context context) {
        ArrayList<OscProfile> profiles = new ArrayList<>();
        SharedPreferences meta = getMetaPreferences(context);
        boolean hadProfileMetadata = meta.contains(KEY_PROFILES);
        Object rawSerialized = meta.getAll().get(KEY_PROFILES);
        String serialized = rawSerialized instanceof String ? (String) rawSerialized : null;
        boolean metadataNeedsRewrite = false;
        boolean lostProfileEntry = false;

        if (serialized != null && !serialized.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(serialized);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        metadataNeedsRewrite = true;
                        lostProfileEntry = true;
                        continue;
                    }

                    try {
                        OscProfile profile = OscProfile.fromJson(object);
                        String id = profile.getId();
                        if (id == null || id.trim().isEmpty()) {
                            metadataNeedsRewrite = true;
                            lostProfileEntry = true;
                            continue;
                        }
                        if (findById(profiles, id) == null) {
                            profiles.add(profile);
                        } else {
                            // Deduplication repairs metadata but is not evidence that a distinct
                            // profile vanished, so do not resurrect unrelated stale references.
                            metadataNeedsRewrite = true;
                        }
                    } catch (JSONException ignored) {
                        // Keep valid sibling entries instead of turning one broken profile into a
                        // catastrophic loss of the complete profile list.
                        metadataNeedsRewrite = true;
                        lostProfileEntry = true;
                    }
                }
            } catch (JSONException ignored) {
                profiles.clear();
                metadataNeedsRewrite = true;
                lostProfileEntry = true;
            }
        } else if (hadProfileMetadata) {
            // A present-but-non-string/blank profile value is damaged existing metadata, not the
            // normal first-run state where the key is absent entirely.
            metadataNeedsRewrite = true;
            lostProfileEntry = true;
        }

        if (!hadProfileMetadata) {
            // A missing profile list is the normal first-run state. Do not promote stale mappings
            // left by an old install into real profiles merely because metadata was never initialized.
            profiles.add(defaultProfile());
            writeProfiles(context, profiles);
        } else if (profiles.isEmpty()) {
            profiles = recoverProfilesFromMetadata(meta);
            writeProfiles(context, profiles);
        } else if (lostProfileEntry) {
            // Partial corruption is still data loss. Merge IDs proven by active/game references or
            // initialized snapshots while preserving the names/order of every valid sibling.
            mergeRecoveredProfiles(profiles, recoverProfilesFromMetadata(meta));
            ensureDefaultProfile(profiles);
            writeProfiles(context, profiles);
        } else if (metadataNeedsRewrite) {
            writeProfiles(context, profiles);
        }
        return profiles;
    }

    /**
     * Reconstruct the minimum safe profile list after profile-list loss. Existing active/game
     * references and true snapshot markers are durable evidence that a profile ID was user-owned.
     * Callers only use these references when stored profile metadata is demonstrably damaged;
     * ordinary first-run or stale-reference repair therefore keeps its existing behavior.
     */
    private static ArrayList<OscProfile> recoverProfilesFromMetadata(SharedPreferences meta) {
        TreeSet<String> recoveredIds = new TreeSet<>();
        Map<String, ?> values = meta.getAll();

        addRecoverableId(recoveredIds, values.get(KEY_ACTIVE_PROFILE));
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.startsWith(KEY_GAME_PROFILE_PREFIX)) {
                addRecoverableId(recoveredIds, value);
            } else if (key.startsWith(KEY_SNAPSHOT_INITIALIZED_PREFIX)
                    && Boolean.TRUE.equals(value)) {
                addRecoverableId(recoveredIds,
                        key.substring(KEY_SNAPSHOT_INITIALIZED_PREFIX.length()));
            }
        }

        recoveredIds.remove(OscProfile.DEFAULT_ID);
        ArrayList<OscProfile> profiles = new ArrayList<>();
        profiles.add(defaultProfile());
        int recoveredIndex = 1;
        for (String profileId : recoveredIds) {
            profiles.add(new OscProfile(profileId, "Recovered OSC Profile " + recoveredIndex++));
        }
        return profiles;
    }

    private static void mergeRecoveredProfiles(List<OscProfile> profiles,
                                               List<OscProfile> recoveredProfiles) {
        for (OscProfile recovered : recoveredProfiles) {
            if (recovered.isDefault()) {
                continue;
            }
            if (findById(profiles, recovered.getId()) == null) {
                profiles.add(recovered);
            }
        }
    }

    private static void addRecoverableId(Set<String> recoveredIds, Object rawId) {
        if (!(rawId instanceof String)) {
            return;
        }
        String id = (String) rawId;
        if (id.trim().isEmpty() || id.length() > MAX_RECOVERED_ID_LENGTH) {
            return;
        }
        recoveredIds.add(id);
    }

    /** @return true when the list was repaired. */
    private static boolean ensureDefaultProfile(List<OscProfile> profiles) {
        if (findById(profiles, OscProfile.DEFAULT_ID) != null) {
            return false;
        }
        profiles.add(0, defaultProfile());
        return true;
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
