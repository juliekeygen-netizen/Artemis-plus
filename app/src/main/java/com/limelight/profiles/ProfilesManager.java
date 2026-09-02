package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.AtomicFile;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProfilesManager {
    private static final String PROFILES_DIR = "profiles";
    private static final String PROFILES_FILE = "profiles.json";

    static ProfilesManager instance;

    private final Map<UUID, SettingsProfile> profiles = new LinkedHashMap<>();
    private UUID activeProfileId;
    private final List<ProfileChangeListener> listeners = new ArrayList<>();
    private Context appContext; // Application context for auto-save

    private ProfilesManager() {}

    public static synchronized ProfilesManager getInstance() {
        if (instance == null) {
            instance = new ProfilesManager();
        }
        return instance;
    }

    public synchronized boolean load(Context context) {
        LimeLog.info("ArtemisProfile: Loading profile...");
        if (context == null) {
            return false;
        }

        try {
            this.appContext = context.getApplicationContext();
        } catch (Exception e) {
            // If getApplicationContext() fails (e.g., during app startup), use the context directly
            this.appContext = context;
        }

        // Additional safety check
        if (this.appContext == null) {
            return false;
        }

        try {
            File dir = new File(this.appContext.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            AtomicFile file = new AtomicFile(new File(dir, PROFILES_FILE));
            try (Reader reader = new InputStreamReader(file.openRead(), StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                Type type = new TypeToken<ProfilesData>(){}.getType();
                ProfilesData data = gson.fromJson(reader, type);
                if (data != null && data.profiles != null) {
                    Map<UUID, SettingsProfile> loadedProfiles = new LinkedHashMap<>();
                    for (SettingsProfile p : data.profiles) {
                        if (p == null || p.getUuid() == null) {
                            throw new IllegalArgumentException("Profile data contains a missing UUID");
                        }
                        loadedProfiles.put(p.getUuid(), p);
                    }
                    UUID loadedActiveProfileId = data.activeProfileId;
                    if (loadedActiveProfileId != null && !loadedProfiles.containsKey(loadedActiveProfileId)) {
                        loadedActiveProfileId = null;
                    }
                    profiles.clear();
                    profiles.putAll(loadedProfiles);
                    activeProfileId = loadedActiveProfileId;
                }
            } catch (FileNotFoundException e) {
                // We don't want to warn the user when profiles have never been created.
                return true;
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to load profiles from file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to load profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public synchronized boolean save(Context context) {
        if (context == null) {
            return false;
        }

        AtomicFile file = null;
        FileOutputStream output = null;
        try {
            File dir = new File(context.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            file = new AtomicFile(new File(dir, PROFILES_FILE));
            output = file.startWrite();
            Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            ProfilesData data = new ProfilesData();
            data.profiles = new ArrayList<>(profiles.values());
            data.activeProfileId = activeProfileId;
            gson.toJson(data, writer);
            writer.flush();
            file.finishWrite(output);
            return true;
        } catch (Exception e) {
            if (file != null && output != null) {
                file.failWrite(output);
            }
            LimeLog.warning("ArtemisProfile: Failed to save profiles to file:" + e);
            e.printStackTrace();
            return false;
        }
    }

    public List<SettingsProfile> getProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public void add(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void update(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void delete(UUID uuid) {
        profiles.remove(uuid);
        if (uuid.equals(activeProfileId)) {
            activeProfileId = null;
        }
        notifyListeners();
        saveIfPossible();
    }

    public void setActive(UUID uuid) {
        activeProfileId = uuid;
        notifyListeners();
        saveIfPossible();
    }

    public SettingsProfile getActive() {
        return activeProfileId == null ? null : profiles.get(activeProfileId);
    }

    @NonNull
    public String getActiveName() {
        SettingsProfile active = getActive();
        return active == null ? "" : active.getName();
    }

    public void addListener(ProfileChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProfileChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ProfileChangeListener listener : listeners) {
            listener.onProfilesChanged();
        }
    }

    private static class ProfilesData {
        List<SettingsProfile> profiles;
        UUID activeProfileId;
    }

    public interface ProfileChangeListener {
        void onProfilesChanged();
    }

    /**
     * Returns a SharedPreferences that overlays the active profile's options on top of the real prefs.
     */
    public SharedPreferences getOverlayingSharedPreferences(Context context) {
        SharedPreferences base = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SettingsProfile active = getActive();
        Map<String, Object> patch = active == null || active.getOptions() == null
                ? java.util.Collections.emptyMap()
                : active.getOptions();
        return new OverlaySharedPreferences(base, patch);
    }

    /**
     * Wraps a SharedPreferences to override and shadow values from a profile's options map.
     */
    private static class OverlaySharedPreferences implements SharedPreferences {
        private final SharedPreferences base;
        private final Map<String, Object> patch;
        OverlaySharedPreferences(SharedPreferences base, Map<String, Object> patch) {
            this.base = base;
            this.patch = patch;
        }
        @Override public Map<String, ?> getAll() {
            Map<String, Object> combined = new LinkedHashMap<>(base.getAll());
            combined.putAll(patch);
            return combined;
        }
        @Override public String getString(String key, String defValue) {
            if (patch.containsKey(key)) {
                Object value = patch.get(key);
                if (value instanceof String) {
                    return (String) value;
                }
            }
            try {
                return base.getString(key, defValue);
            } catch (ClassCastException e) {
                return defValue;
            }
        }
        @Override public int getInt(String key, int defValue) {
            if (patch.containsKey(key)) {
                Object value = patch.get(key);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
            try {
                return base.getInt(key, defValue);
            } catch (ClassCastException e) {
                return defValue;
            }
        }
        @Override public long getLong(String key, long defValue) {
            if (patch.containsKey(key)) {
                Object value = patch.get(key);
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            }
            try {
                return base.getLong(key, defValue);
            } catch (ClassCastException e) {
                return defValue;
            }
        }
        @Override public float getFloat(String key, float defValue) {
            if (patch.containsKey(key)) {
                Object value = patch.get(key);
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
            }
            try {
                return base.getFloat(key, defValue);
            } catch (ClassCastException e) {
                return defValue;
            }
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            if (patch.containsKey(key)) {
                Object value = patch.get(key);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
            }
            try {
                return base.getBoolean(key, defValue);
            } catch (ClassCastException e) {
                return defValue;
            }
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            if (patch.containsKey(key)) {
                Set<String> value = toStringSet(patch.get(key));
                if (value != null) {
                    return value;
                }
            }
            try {
                return base.getStringSet(key, defValues);
            } catch (ClassCastException e) {
                return defValues;
            }
        }
        private static Set<String> toStringSet(Object value) {
            if (!(value instanceof Iterable<?>)) {
                return null;
            }
            Set<String> strings = new LinkedHashSet<>();
            for (Object item : (Iterable<?>) value) {
                if (!(item instanceof String)) {
                    return null;
                }
                strings.add((String) item);
            }
            return strings;
        }
        @Override public boolean contains(String key) {
            return patch.containsKey(key) || base.contains(key);
        }
        @Override public Editor edit() { return base.edit(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.registerOnSharedPreferenceChangeListener(listener);
        }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private boolean saveIfPossible() {
        if (appContext != null) {
            return save(appContext);
        }
        return false;
    }
}
