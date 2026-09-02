package com.limelight;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Type-tolerant value access and saved-state encoding for settings profile editing. */
final class ProfileEditorPreferenceValues {
    private ProfileEditorPreferenceValues() {
    }

    static float getFloat(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    static Set<String> getStringSet(Object value, Set<String> fallback) {
        if (!(value instanceof Iterable<?>)) {
            return fallback;
        }

        LinkedHashSet<String> strings = new LinkedHashSet<>();
        for (Object item : (Iterable<?>) value) {
            if (!(item instanceof String)) {
                return fallback;
            }
            strings.add((String) item);
        }
        return strings;
    }

    static Bundle encodeState(Map<String, ?> values) {
        Bundle state = new Bundle();
        if (values == null) {
            return state;
        }

        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                state.putString(key, (String) value);
            } else if (value instanceof Integer) {
                state.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                state.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                state.putFloat(key, (Float) value);
            } else if (value instanceof Double) {
                state.putDouble(key, (Double) value);
            } else if (value instanceof Boolean) {
                state.putBoolean(key, (Boolean) value);
            } else if (value instanceof Iterable<?>) {
                ArrayList<String> copy = copyStrings((Iterable<?>) value);
                if (copy != null) {
                    state.putStringArrayList(key, copy);
                }
            }
        }
        return state;
    }

    static Map<String, Object> decodeState(Bundle state) {
        if (state == null) {
            return null;
        }

        Map<String, Object> values = new HashMap<>();
        for (String key : state.keySet()) {
            Object value = state.get(key);
            if (value instanceof ArrayList<?>) {
                ArrayList<String> strings = copyStrings((ArrayList<?>) value);
                if (strings != null) {
                    values.put(key, new LinkedHashSet<>(strings));
                }
            } else if (value instanceof String || value instanceof Integer || value instanceof Long ||
                    value instanceof Float || value instanceof Double || value instanceof Boolean) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static ArrayList<String> copyStrings(Iterable<?> values) {
        ArrayList<String> strings = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String)) {
                return null;
            }
            strings.add((String) item);
        }
        return strings;
    }
}
