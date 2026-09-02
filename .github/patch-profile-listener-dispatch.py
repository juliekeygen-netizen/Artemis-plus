from pathlib import Path

manager_path = Path('app/src/main/java/com/limelight/profiles/ProfilesManager.java')
manager = manager_path.read_text(encoding='utf-8')
old = '''    private void notifyListeners() {
        for (ProfileChangeListener listener : listeners) {
            listener.onProfilesChanged();
        }
    }'''
new = '''    private void notifyListeners() {
        // Dispatch from a snapshot so callbacks may safely add/remove listeners without
        // invalidating this notification pass or causing later listeners to be skipped.
        for (ProfileChangeListener listener : new ArrayList<>(listeners)) {
            listener.onProfilesChanged();
        }
    }'''
if manager.count(old) != 1:
    raise SystemExit(f'Expected exactly one listener dispatch block, found {manager.count(old)}')
manager_path.write_text(manager.replace(old, new, 1), encoding='utf-8')

test_path = Path('app/src/test/java/com/limelight/profiles/ProfilesManagerTest.java')
test = test_path.read_text(encoding='utf-8')
anchor = '''    @Test
    public void interruptedWrite_restoresLastCommittedProfiles() throws Exception {'''
addition = '''    @Test
    public void listenerCanRemoveItselfWithoutSkippingLaterListeners() {
        int[] firstCalls = {0};
        int[] secondCalls = {0};
        ProfilesManager.ProfileChangeListener[] first = new ProfilesManager.ProfileChangeListener[1];
        first[0] = () -> {
            firstCalls[0]++;
            manager.removeListener(first[0]);
        };
        ProfilesManager.ProfileChangeListener second = () -> secondCalls[0]++;
        manager.addListener(first[0]);
        manager.addListener(second);

        SettingsProfile profile = new SettingsProfile(
                UUID.randomUUID(), "ListenerTest", System.currentTimeMillis(),
                System.currentTimeMillis(), null);
        manager.add(profile);

        assertEquals(1, firstCalls[0]);
        assertEquals(1, secondCalls[0]);

        manager.update(profile);
        assertEquals(1, firstCalls[0]);
        assertEquals(2, secondCalls[0]);
    }

'''
if test.count(anchor) != 1:
    raise SystemExit(f'Expected exactly one test insertion anchor, found {test.count(anchor)}')
test_path.write_text(test.replace(anchor, addition + anchor, 1), encoding='utf-8')
