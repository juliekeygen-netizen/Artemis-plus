from pathlib import Path

path = Path('app/src/main/java/com/limelight/EditProfileActivity.java')
text = path.read_text(encoding='utf-8')

replacements = [
    ('''    private static Bundle encodePreferenceState(Map<String, ?> values) {
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
            } else if (value instanceof Boolean) {
                state.putBoolean(key, (Boolean) value);
            } else if (value instanceof java.util.Set) {
                java.util.ArrayList<String> copy = new java.util.ArrayList<>();
                for (Object item : (java.util.Set<?>) value) {
                    if (item instanceof String) {
                        copy.add((String) item);
                    }
                }
                state.putStringArrayList(key, copy);
            }
        }
        return state;
    }''', '''    private static Bundle encodePreferenceState(Map<String, ?> values) {
        return ProfileEditorPreferenceValues.encodeState(values);
    }'''),
    ('''    private static Map<String, Object> decodePreferenceState(Bundle state) {
        if (state == null) {
            return null;
        }
        Map<String, Object> values = new HashMap<>();
        for (String key : state.keySet()) {
            Object value = state.get(key);
            if (value instanceof java.util.ArrayList) {
                java.util.HashSet<String> set = new java.util.HashSet<>();
                for (Object item : (java.util.ArrayList<?>) value) {
                    if (item instanceof String) {
                        set.add((String) item);
                    }
                }
                values.put(key, set);
            } else if (value instanceof String || value instanceof Integer || value instanceof Long ||
                    value instanceof Float || value instanceof Boolean) {
                values.put(key, value);
            }
        }
        return values;
    }''', '''    private static Map<String, Object> decodePreferenceState(Bundle state) {
        return ProfileEditorPreferenceValues.decodeState(state);
    }'''),
    ('''        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }''', '''        @Override
        public float getFloat(String key, float defValue) {
            return ProfileEditorPreferenceValues.getFloat(values.get(key), defValue);
        }'''),
    ('''        @Override
        public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) {
            Object value = values.get(key);
            return value instanceof java.util.Set
                    ? new java.util.HashSet<>((java.util.Set<String>) value)
                    : defValues;
        }''', '''        @Override
        public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) {
            return ProfileEditorPreferenceValues.getStringSet(values.get(key), defValues);
        }'''),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one EditProfileActivity block, found {count}: {old.splitlines()[0]}')
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
