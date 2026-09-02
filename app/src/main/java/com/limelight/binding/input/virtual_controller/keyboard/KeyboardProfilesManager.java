package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent profile manager for the special/custom-key overlay.
 *
 * The legacy implementation exposed five anonymous SharedPreferences files through a ListPreference.
 * Artemis Plus keeps the same underlying per-layout preference-file format for compatibility, but
 * gives those files real user-managed profile metadata (name/order/active profile) and allows an
 * unlimited number of profiles.
 */
public final class KeyboardProfilesManager {
    private static final String META_PREFS = "ArtemisPlusKeyboardProfiles";
    private static final String KEY_PROFILES = "profiles_v1";
    private static final String KEY_ACTIVE = "active_profile_id";
    private static final String EXPORT_FORMAT = "artemis-plus-keyboard-profiles";
    private static final int EXPORT_VERSION = 1;

    private static final String[] LEGACY_STORAGES = {
            "OSC_Keyboard", "OSC_Keyboard_2", "OSC_Keyboard_3", "OSC_Keyboard_4", "OSC_Keyboard_5"
    };

    private KeyboardProfilesManager() {
    }

    public static final class Profile {
        public final String id;
        public final String name;
        public final String storageName;

        Profile(String id, String name, String storageName) {
            this.id = id;
            this.name = name;
            this.storageName = storageName;
        }

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("name", name);
            object.put("storage", storageName);
            return object;
        }

        static Profile fromJson(JSONObject object) throws JSONException {
            return new Profile(
                    object.getString("id"),
                    object.optString("name", "Profile"),
                    object.getString("storage"));
        }
    }

    public static synchronized void ensureInitialized(Context context) {
        List<Profile> profiles = readProfiles(context);
        if (!profiles.isEmpty()) {
            repairActive(context, profiles);
            return;
        }

        SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);
        String selectedLegacy = SafePreferenceValues.getString(
                defaults,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);

        List<Profile> migrated = new ArrayList<>();
        int index = 1;
        for (String storage : LEGACY_STORAGES) {
            Map<String, ?> values = context.getSharedPreferences(storage, Context.MODE_PRIVATE).getAll();
            if (!values.isEmpty() || storage.equals(selectedLegacy)) {
                migrated.add(new Profile(newId(), "Profile " + index++, storage));
            }
        }

        // Preserve a non-standard legacy layout name too, if one was selected by an older fork.
        boolean selectedIncluded = false;
        for (Profile profile : migrated) {
            if (profile.storageName.equals(selectedLegacy)) {
                selectedIncluded = true;
                break;
            }
        }
        if (!selectedIncluded && selectedLegacy != null && !selectedLegacy.isEmpty()) {
            migrated.add(new Profile(newId(), "Profile " + index++, selectedLegacy));
        }

        if (migrated.isEmpty()) {
            migrated.add(new Profile(newId(), "Profile 1", KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE));
        }

        String activeId = migrated.get(0).id;
        for (Profile profile : migrated) {
            if (profile.storageName.equals(selectedLegacy)) {
                activeId = profile.id;
                break;
            }
        }
        writeProfiles(context, migrated, activeId);
        syncLegacyActiveStorage(context, findById(migrated, activeId).storageName);
    }

    public static synchronized List<Profile> getProfiles(Context context) {
        ensureInitialized(context);
        return new ArrayList<>(readProfiles(context));
    }

    public static synchronized Profile getActiveProfile(Context context) {
        ensureInitialized(context);
        List<Profile> profiles = readProfiles(context);
        String activeId = readActiveId(context, "");
        Profile active = findById(profiles, activeId);
        if (active == null) {
            active = profiles.get(0);
            writeProfiles(context, profiles, active.id);
        }
        syncLegacyActiveStorage(context, active.storageName);
        return active;
    }

    public static synchronized String getActiveStorageName(Context context) {
        return getActiveProfile(context).storageName;
    }

    public static synchronized boolean setActiveProfile(Context context, String profileId) {
        ensureInitialized(context);
        List<Profile> profiles = readProfiles(context);
        Profile profile = findById(profiles, profileId);
        if (profile == null) {
            return false;
        }
        meta(context).edit().putString(KEY_ACTIVE, profile.id).apply();
        syncLegacyActiveStorage(context, profile.storageName);
        return true;
    }

    public static synchronized Profile createProfile(Context context, String requestedName) {
        ensureInitialized(context);
        List<Profile> profiles = readProfiles(context);
        String name = uniqueName(profiles, cleanName(requestedName, "Profile " + (profiles.size() + 1)), null);
        Profile profile = new Profile(newId(), name, newStorageName());
        profiles.add(profile);
        writeProfiles(context, profiles, readActiveId(context, profiles.get(0).id));
        return profile;
    }

    public static synchronized Profile duplicateProfile(Context context, String profileId) {
        ensureInitialized(context);
        List<Profile> profiles = readProfiles(context);
        Profile source = findById(profiles, profileId);
        if (source == null) {
            return null;
        }

        Profile duplicate = new Profile(
                newId(),
                uniqueName(profiles, source.name + " copy", null),
                newStorageName());
        copyPreferenceFile(context, source.storageName, duplicate.storageName);
        KeyComboManager.copyDefinitionsForLayout(context, source.storageName, duplicate.storageName);
        ArtemisActionButtonFactory.copySelectionForLayout(context, source.storageName, duplicate.storageName);

        int sourceIndex = profiles.indexOf(source);
        profiles.add(sourceIndex + 1, duplicate);
        writeProfiles(context, profiles, readActiveId(context, profiles.get(0).id));
        return duplicate;
    }

    public static synchronized boolean renameProfile(Context context, String profileId, String requestedName) {
        ensureInitialized(context);
        List<Profile> profiles = readProfiles(context);
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = profiles.get(i);
            if (profile.id.equals(profileId)) {
                String name = uniqueName(profiles, cleanName(requestedName, profile.name), profileId);
                profiles.set(i, new Profile(profile.id, name, profile.storageName));
                writeProfiles(context, profiles, readActiveId(context, profiles.get(0).id));
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean deleteProfile(Context context, String profileId) {
        ensureInitialized(context);
        List<Profile> profiles = readProfiles(context);
        if (profiles.size() <= 1) {
            return false;
        }

        Profile victim = findById(profiles, profileId);
        if (victim == null) {
            return false;
        }
        profiles.remove(victim);

        context.getSharedPreferences(victim.storageName, Context.MODE_PRIVATE).edit().clear().apply();
        KeyComboManager.deleteDefinitionsForLayout(context, victim.storageName);
        ArtemisActionButtonFactory.deleteSelectionForLayout(context, victim.storageName);

        String activeId = readActiveId(context, "");
        if (victim.id.equals(activeId)) {
            activeId = profiles.get(Math.max(0, Math.min(profiles.size() - 1, 0))).id;
        }
        writeProfiles(context, profiles, activeId);
        Profile active = findById(profiles, activeId);
        if (active == null) {
            active = profiles.get(0);
        }
        syncLegacyActiveStorage(context, active.storageName);
        return true;
    }

    /** Move one profile one slot up/down. */
    public static synchronized boolean moveProfile(Context context, String profileId, int direction) {
        ensureInitialized(context);
        if (direction == 0) {
            return false;
        }
        List<Profile> profiles = readProfiles(context);
        int from = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profileId)) {
                from = i;
                break;
            }
        }
        if (from < 0) {
            return false;
        }
        int to = from + (direction < 0 ? -1 : 1);
        if (to < 0 || to >= profiles.size()) {
            return false;
        }
        Collections.swap(profiles, from, to);
        writeProfiles(context, profiles, readActiveId(context, profiles.get(0).id));
        return true;
    }

    /** Export all keyboard profiles, including custom key definitions and Artemis action selections. */
    public static synchronized JSONObject exportProfiles(Context context) throws JSONException {
        ensureInitialized(context);
        JSONObject root = new JSONObject();
        root.put("format", EXPORT_FORMAT);
        root.put("version", EXPORT_VERSION);
        root.put("activeProfile", readActiveId(context, ""));

        JSONArray profileArray = new JSONArray();
        for (Profile profile : readProfiles(context)) {
            JSONObject exported = new JSONObject();
            exported.put("name", profile.name);

            JSONObject layout = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(
                    profile.storageName, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    layout.put(entry.getKey(), value);
                }
            }
            exported.put("layout", layout);
            exported.put("keys", KeyComboManager.exportDefinitionsForLayout(context, profile.storageName));
            exported.put("actions", ArtemisActionButtonFactory.exportSelectionForLayout(context, profile.storageName));
            profileArray.put(exported);
        }
        root.put("profiles", profileArray);
        return root;
    }

    /**
     * Import a profile bundle without replacing any current profiles. Legacy single-layout JSON is
     * accepted too and becomes one new profile.
     *
     * @return number of profiles added
     */
    public static synchronized int importProfiles(Context context, String json) throws JSONException {
        ensureInitialized(context);
        JSONObject root = new JSONObject(json);
        JSONArray importedProfiles;
        boolean modernBundle = root.has("format");

        if (modernBundle) {
            String format = root.optString("format", "");
            if (!EXPORT_FORMAT.equals(format)) {
                throw new JSONException("Unsupported keyboard profile bundle format: " + format);
            }
            int version = root.has("version")
                    ? root.optInt("version", -1)
                    : EXPORT_VERSION;
            if (version != EXPORT_VERSION) {
                throw new JSONException("Unsupported keyboard profile bundle version: " + version);
            }
            importedProfiles = root.optJSONArray("profiles");
            if (importedProfiles == null) {
                throw new JSONException("Missing profiles array");
            }
        } else {
            // Old Artemis/Diana exports were simply a map of elementId -> serialized geometry.
            importedProfiles = new JSONArray();
            JSONObject legacy = new JSONObject();
            legacy.put("name", "Imported Profile");
            legacy.put("layout", root);
            legacy.put("keys", new JSONArray());
            legacy.put("actions", new JSONArray());
            importedProfiles.put(legacy);
        }

        // Validate the complete structure before writing any new preference files. This keeps a
        // malformed later profile from leaving orphaned geometry/key/action state behind after an
        // otherwise failed import.
        List<JSONObject> validatedProfiles = new ArrayList<>();
        for (int i = 0; i < importedProfiles.length(); i++) {
            Object value = importedProfiles.opt(i);
            if (!(value instanceof JSONObject)) {
                throw new JSONException("Invalid profile entry at index " + i);
            }
            JSONObject imported = (JSONObject) value;
            JSONObject layout = imported.optJSONObject("layout");
            if (layout == null) {
                if (modernBundle) {
                    throw new JSONException("Missing layout object for profile at index " + i);
                }
                continue;
            }
            validateOptionalArray(imported, "keys", i);
            validateOptionalArray(imported, "actions", i);
            validatedProfiles.add(imported);
        }

        List<Profile> existing = readProfiles(context);
        int added = 0;
        for (JSONObject imported : validatedProfiles) {
            JSONObject layout = imported.getJSONObject("layout");
            String requestedName = imported.optString("name", "Imported Profile");
            Profile profile = new Profile(
                    newId(),
                    uniqueName(existing, cleanName(requestedName, "Imported Profile"), null),
                    newStorageName());

            SharedPreferences.Editor editor = context.getSharedPreferences(
                    profile.storageName, Context.MODE_PRIVATE).edit();
            IteratorHelper.copyJsonObjectToPreferences(layout, editor);
            editor.apply();

            JSONArray keys = imported.optJSONArray("keys");
            if (keys != null) {
                KeyComboManager.importDefinitionsForLayout(context, profile.storageName, keys);
            }
            JSONArray actions = imported.optJSONArray("actions");
            if (actions != null) {
                ArtemisActionButtonFactory.importSelectionForLayout(context, profile.storageName, actions);
            }

            existing.add(profile);
            added++;
        }

        if (added > 0) {
            writeProfiles(context, existing, readActiveId(context, existing.get(0).id));
        }
        return added;
    }

    private static void validateOptionalArray(JSONObject object, String key, int profileIndex)
            throws JSONException {
        if (!object.has(key)) {
            return;
        }
        Object value = object.opt(key);
        if (value != null && value != JSONObject.NULL && !(value instanceof JSONArray)) {
            throw new JSONException("Invalid " + key + " array for profile at index " + profileIndex);
        }
    }

    private static String readActiveId(Context context, String fallback) {
        return SafePreferenceValues.getString(meta(context), KEY_ACTIVE, fallback);
    }

    private static SharedPreferences meta(Context context) {
        return context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE);
    }

    private static List<Profile> readProfiles(Context context) {
        SharedPreferences metadata = meta(context);
        String serialized = SafePreferenceValues.getString(metadata, KEY_PROFILES, "[]");
        List<Profile> profiles = new ArrayList<>();
        JSONArray array;
        try {
            array = new JSONArray(serialized);
        } catch (JSONException ignored) {
            return profiles;
        }

        JSONArray repairedArray = new JSONArray();
        boolean corruptedEntry = false;
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (!(value instanceof JSONObject)) {
                corruptedEntry = true;
                continue;
            }
            JSONObject object = (JSONObject) value;
            try {
                Profile profile = Profile.fromJson(object);
                if (profile.id.isEmpty() || profile.storageName.isEmpty()
                        || findById(profiles, profile.id) != null
                        || findByStorageName(profiles, profile.storageName) != null) {
                    corruptedEntry = true;
                    continue;
                }
                profiles.add(profile);
                // Keep the original object so fields added by newer clients survive older-client repair.
                repairedArray.put(object);
            } catch (JSONException ignored) {
                corruptedEntry = true;
            }
        }

        // Keep valid siblings while preserving any unknown fields on those valid metadata objects.
        if (corruptedEntry && !profiles.isEmpty()) {
            metadata.edit().putString(KEY_PROFILES, repairedArray.toString()).apply();
        }
        return profiles;
    }

    private static void writeProfiles(Context context, List<Profile> profiles, String activeId) {
        JSONArray array = new JSONArray();
        for (Profile profile : profiles) {
            try {
                array.put(profile.toJson());
            } catch (JSONException ignored) {
            }
        }
        meta(context).edit()
                .putString(KEY_PROFILES, array.toString())
                .putString(KEY_ACTIVE, activeId)
                .apply();
    }

    private static void repairActive(Context context, List<Profile> profiles) {
        String activeId = readActiveId(context, "");
        Profile active = findById(profiles, activeId);
        if (active == null) {
            active = profiles.get(0);
            meta(context).edit().putString(KEY_ACTIVE, active.id).apply();
        }
        syncLegacyActiveStorage(context, active.storageName);
    }

    private static void syncLegacyActiveStorage(Context context, String storageName) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, storageName)
                .apply();
    }

    private static Profile findById(List<Profile> profiles, String id) {
        if (id == null) {
            return null;
        }
        for (Profile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    private static Profile findByStorageName(List<Profile> profiles, String storageName) {
        if (storageName == null) {
            return null;
        }
        for (Profile profile : profiles) {
            if (profile.storageName.equals(storageName)) {
                return profile;
            }
        }
        return null;
    }

    private static String cleanName(String requested, String fallback) {
        String value = requested == null ? "" : requested.trim();
        return value.isEmpty() ? fallback : value;
    }

    private static String uniqueName(List<Profile> profiles, String requested, String ignoreId) {
        String base = requested;
        String candidate = base;
        int suffix = 2;
        while (nameExists(profiles, candidate, ignoreId)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static boolean nameExists(List<Profile> profiles, String name, String ignoreId) {
        for (Profile profile : profiles) {
            if ((ignoreId == null || !profile.id.equals(ignoreId)) && profile.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static void copyPreferenceFile(Context context, String from, String to) {
        SharedPreferences source = context.getSharedPreferences(from, Context.MODE_PRIVATE);
        SharedPreferences.Editor target = context.getSharedPreferences(to, Context.MODE_PRIVATE).edit().clear();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                target.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                target.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                target.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                target.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                target.putFloat(entry.getKey(), (Float) value);
            }
        }
        target.apply();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String newStorageName() {
        return "ArtemisKeyboardProfile_" + newId();
    }

    /** Tiny JSON->SharedPreferences bridge kept here so legacy imports remain type-safe. */
    private static final class IteratorHelper {
        static void copyJsonObjectToPreferences(JSONObject object, SharedPreferences.Editor editor) throws JSONException {
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = object.get(key);
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Double) {
                    double number = (Double) value;
                    if (number == Math.rint(number) && number <= Integer.MAX_VALUE && number >= Integer.MIN_VALUE) {
                        editor.putInt(key, (int) number);
                    } else {
                        editor.putFloat(key, (float) number);
                    }
                } else {
                    editor.putString(key, String.valueOf(value));
                }
            }
        }
    }
}
