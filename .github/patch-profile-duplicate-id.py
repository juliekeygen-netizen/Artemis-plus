from pathlib import Path

manager_path = Path('app/src/main/java/com/limelight/profiles/ProfilesManager.java')
manager = manager_path.read_text(encoding='utf-8')
old = '''                        loadedProfiles.put(p.getUuid(), p);'''
new = '''                        if (loadedProfiles.put(p.getUuid(), p) != null) {
                            throw new IllegalArgumentException("Profile data contains a duplicate UUID: " + p.getUuid());
                        }'''
if manager.count(old) != 1:
    raise SystemExit(f'Expected exactly one profile insert, found {manager.count(old)}')
manager_path.write_text(manager.replace(old, new, 1), encoding='utf-8')

test_path = Path('app/src/test/java/com/limelight/profiles/ProfilesManagerTest.java')
test = test_path.read_text(encoding='utf-8')
anchor = '''    private ProfilesManager reloadManager() {'''
addition = '''    @Test
    public void duplicateProfileIds_doNotReplaceLiveProfiles() throws Exception {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "KeepMe", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());

        UUID duplicateId = UUID.randomUUID();
        String duplicateJson = "{\\\"profiles\\\":[" +
                "{\\\"uuid\\\":\\\"" + duplicateId + "\\\",\\\"name\\\":\\\"First\\\",\\\"createdUtc\\\":1,\\\"modifiedUtc\\\":1,\\\"options\\\":{}}," +
                "{\\\"uuid\\\":\\\"" + duplicateId + "\\\",\\\"name\\\":\\\"Second\\\",\\\"createdUtc\\\":2,\\\"modifiedUtc\\\":2,\\\"options\\\":{}}" +
                "],\\\"activeProfileId\\\":null}";
        File profilesFile = new File(profilesDir, "profiles.json");
        try (FileOutputStream output = new FileOutputStream(profilesFile, false)) {
            output.write(duplicateJson.getBytes(StandardCharsets.UTF_8));
        }

        assertFalse(manager.load(context));
        assertEquals(1, manager.getProfiles().size());
        assertEquals(p.getUuid(), manager.getProfiles().get(0).getUuid());
    }

'''
if test.count(anchor) != 1:
    raise SystemExit(f'Expected exactly one test anchor, found {test.count(anchor)}')
test_path.write_text(test.replace(anchor, addition + anchor, 1), encoding='utf-8')
