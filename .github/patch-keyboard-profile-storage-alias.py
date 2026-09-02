from pathlib import Path

path = Path("app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/KeyboardProfilesManager.java")
text = path.read_text(encoding="utf-8")

old_validation = '''                if (profile.id.isEmpty() || profile.storageName.isEmpty() || findById(profiles, profile.id) != null) {
                    corruptedEntry = true;
                    continue;
                }
'''
new_validation = '''                if (profile.id.isEmpty() || profile.storageName.isEmpty()
                        || findById(profiles, profile.id) != null
                        || findByStorageName(profiles, profile.storageName) != null) {
                    corruptedEntry = true;
                    continue;
                }
'''

old_helper = '''    private static Profile findById(List<Profile> profiles, String id) {
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

'''
new_helper = '''    private static Profile findById(List<Profile> profiles, String id) {
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

'''

for old, new, label in [
    (old_validation, new_validation, "profile validation"),
    (old_helper, new_helper, "profile lookup helper"),
]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} block, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
