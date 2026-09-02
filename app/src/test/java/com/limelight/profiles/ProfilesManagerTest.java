package com.limelight.profiles;

import android.content.Context;
import android.util.AtomicFile;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.*;

import com.limelight.TestLogSuppressor;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ProfilesManagerTest {
    private Context context;
    private ProfilesManager manager;
    private File profilesDir;

    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        ProfilesManager.instance = null; // Reset the singleton before each test
        context = ApplicationProvider.getApplicationContext();
        profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        manager = ProfilesManager.getInstance();
        manager.load(context);
    }

    @After
    public void tearDown() {
        ProfilesManager.instance = null;
        deleteRecursively(profilesDir);
    }

    @Test
    public void addAndRetrieveProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Test", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());
        assertEquals(p.getUuid(), manager.getProfiles().get(0).getUuid());
    }

    @Test
    public void setActivePersists() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Active", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());

        ProfilesManager fresh = reloadManager();
        assertNotNull(fresh.getActive());
        assertEquals(p.getUuid(), fresh.getActive().getUuid());
    }

    @Test
    public void updateAndSaveProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Original", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        p.setName("Updated");
        manager.update(p);

        ProfilesManager fresh = reloadManager();
        assertEquals("Updated", fresh.getProfiles().get(0).getName());
    }

    @Test
    public void deleteProfile() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "ToDelete", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());

        manager.delete(p.getUuid());
        assertEquals(0, manager.getProfiles().size());
    }

    @Test
    public void deleteActiveProfile_resetsActive() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "ActiveToDelete", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());
        assertNotNull(manager.getActive());

        manager.delete(p.getUuid());
        assertNull(manager.getActive());

        ProfilesManager fresh = reloadManager();
        assertNull(fresh.getActive());
    }

    @Test
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

    @Test
    public void interruptedWrite_restoresLastCommittedProfiles() throws Exception {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Stable", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        manager.setActive(p.getUuid());
        assertTrue(manager.save(context));

        File profilesFile = new File(profilesDir, "profiles.json");
        AtomicFile atomicFile = new AtomicFile(profilesFile);
        FileOutputStream interrupted = atomicFile.startWrite();
        interrupted.write("{\"profiles\":[".getBytes(StandardCharsets.UTF_8));
        interrupted.flush();
        // Simulate process death after a partial write: the descriptor is closed without commit.
        interrupted.close();

        ProfilesManager fresh = reloadManager();
        assertEquals(1, fresh.getProfiles().size());
        assertEquals(p.getUuid(), fresh.getProfiles().get(0).getUuid());
        assertNotNull(fresh.getActive());
        assertEquals(p.getUuid(), fresh.getActive().getUuid());
    }

    @Test
    public void malformedCommittedData_doesNotReplaceLiveProfiles() throws Exception {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "KeepMe", System.currentTimeMillis(), System.currentTimeMillis(), null);
        manager.add(p);
        assertEquals(1, manager.getProfiles().size());

        File profilesFile = new File(profilesDir, "profiles.json");
        try (FileOutputStream output = new FileOutputStream(profilesFile, false)) {
            output.write("{\"profiles\":[null],\"activeProfileId\":null}".getBytes(StandardCharsets.UTF_8));
        }

        assertFalse(manager.load(context));
        assertEquals(1, manager.getProfiles().size());
        assertEquals(p.getUuid(), manager.getProfiles().get(0).getUuid());
    }

    private ProfilesManager reloadManager() {
        ProfilesManager.instance = null;
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertTrue(fresh.load(context));
        return fresh;
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
