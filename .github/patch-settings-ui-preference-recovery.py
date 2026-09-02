from pathlib import Path

path = Path("app/src/main/java/com/limelight/preferences/StreamSettings.java")
text = path.read_text(encoding="utf-8")

old_fields = '''        private PreferenceConfiguration prevPrefConfig;\n\n        public SettingsFragment() {\n        }\n\n        protected SharedPreferences getPrefs() {\n            return getPreferenceManager().getSharedPreferences();\n        }\n'''
new_fields = '''        private PreferenceConfiguration prevPrefConfig;\n        private RecoveringPreferenceDataStore globalPreferenceStore;\n\n        public SettingsFragment() {\n        }\n\n        protected SharedPreferences getPrefs() {\n            if (globalPreferenceStore != null) {\n                return globalPreferenceStore;\n            }\n            return getPreferenceManager().getSharedPreferences();\n        }\n'''

old_create = '''        @Override\n        public void onCreatePreferences(Bundle bundle, String s) {\n            initializePreferences();\n        }\n'''
new_create = '''        @Override\n        public void onCreatePreferences(Bundle bundle, String s) {\n            // Normal Settings edits the global/base preferences even while a settings profile is\n            // active. Route both the Artemis pre-read and AndroidX preference inflation through a\n            // wrong-type-tolerant adapter without overlaying the active profile. The profile editor\n            // installs its own in-memory PreferenceDataStore before calling this method, so leave\n            // that isolated storage untouched.\n            if (getPreferenceManager().getPreferenceDataStore() == null) {\n                globalPreferenceStore = new RecoveringPreferenceDataStore(\n                        getPreferenceManager().getSharedPreferences());\n                getPreferenceManager().setPreferenceDataStore(globalPreferenceStore);\n            }\n            initializePreferences();\n        }\n'''

for old, new, label in [
    (old_fields, new_fields, "settings preference source"),
    (old_create, new_create, "settings data store installation"),
]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} block, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
