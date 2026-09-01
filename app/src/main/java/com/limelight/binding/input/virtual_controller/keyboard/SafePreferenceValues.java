package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Type-tolerant reads for keyboard/profile preferences that may survive old or damaged installs. */
final class SafePreferenceValues {
    private SafePreferenceValues() {
    }

    static String getString(SharedPreferences preferences, String key, String fallback) {
        Object value = getRaw(preferences, key);
        return value instanceof String ? (String) value : fallback;
    }

    static Set<String> getStringSetCopy(SharedPreferences preferences, String key) {
        Object value = getRaw(preferences, key);
        HashSet<String> copy = new HashSet<>();
        if (!(value instanceof Set<?>)) {
            return copy;
        }
        for (Object member : (Set<?>) value) {
            if (member instanceof String && !((String) member).isEmpty()) {
                copy.add((String) member);
            }
        }
        return copy;
    }

    private static Object getRaw(SharedPreferences preferences, String key) {
        Map<String, ?> values = preferences.getAll();
        return values.containsKey(key) ? values.get(key) : null;
    }
}
