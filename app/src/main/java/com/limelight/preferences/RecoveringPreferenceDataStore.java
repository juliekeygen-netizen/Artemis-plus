package com.limelight.preferences;

import android.content.SharedPreferences;

import androidx.preference.PreferenceDataStore;

import java.util.Map;
import java.util.Set;

/**
 * SharedPreferences adapter that also acts as an AndroidX PreferenceDataStore while tolerating
 * stale values stored under the wrong primitive type. Reads fall back to the caller-provided
 * default instead of throwing ClassCastException; writes still go directly to the wrapped base
 * preferences.
 */
public final class RecoveringPreferenceDataStore extends PreferenceDataStore implements SharedPreferences {
    private final SharedPreferences base;

    public RecoveringPreferenceDataStore(SharedPreferences base) {
        if (base == null) {
            throw new IllegalArgumentException("base SharedPreferences must not be null");
        }
        this.base = base;
    }

    @Override
    public Map<String, ?> getAll() {
        return base.getAll();
    }

    @Override
    public String getString(String key, String defValue) {
        try {
            return base.getString(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        try {
            return base.getStringSet(key, defValues);
        } catch (ClassCastException e) {
            return defValues;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        try {
            return base.getInt(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return base.getLong(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return base.getFloat(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        try {
            return base.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            return defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        return base.contains(key);
    }

    @Override
    public Editor edit() {
        return base.edit();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        base.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        base.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void putString(String key, String value) {
        base.edit().putString(key, value).apply();
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        base.edit().putStringSet(key, values).apply();
    }

    @Override
    public void putInt(String key, int value) {
        base.edit().putInt(key, value).apply();
    }

    @Override
    public void putLong(String key, long value) {
        base.edit().putLong(key, value).apply();
    }

    @Override
    public void putFloat(String key, float value) {
        base.edit().putFloat(key, value).apply();
    }

    @Override
    public void putBoolean(String key, boolean value) {
        base.edit().putBoolean(key, value).apply();
    }
}
