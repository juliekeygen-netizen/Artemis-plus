package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class ClipboardSyncLifecycleTest {
    @Test
    public void sendsShareActivityGenerationUntilDestroy() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();

        long first = lifecycle.beginSend();
        long second = lifecycle.beginSend();

        assertNotEquals(ClipboardSyncLifecycle.INVALID_TOKEN, first);
        assertEquals(first, second);
        assertTrue(lifecycle.isActive(first));
        assertTrue(lifecycle.isActive(second));
    }

    @Test
    public void onlyOneOrdinaryClipboardGetCanOwnTheGeneration() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();

        long first = lifecycle.beginGet();
        assertNotEquals(ClipboardSyncLifecycle.INVALID_TOKEN, first);
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginGet());

        lifecycle.finishGet(first);
        long second = lifecycle.beginGet();
        assertNotEquals(ClipboardSyncLifecycle.INVALID_TOKEN, second);
        assertTrue(lifecycle.isActive(second));
    }

    @Test
    public void destroyInvalidatesOrdinaryWorkAndRejectsNewWork() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();
        long send = lifecycle.beginSend();
        long get = lifecycle.beginGet();

        lifecycle.destroy();

        assertFalse(lifecycle.isActive(send));
        assertFalse(lifecycle.isActive(get));
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginSend());
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginGet());
    }

    @Test
    public void finalDisconnectGetInvalidatesOrdinaryWorkAndSurvivesDestroy() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();
        long send = lifecycle.beginSend();
        long ordinaryGet = lifecycle.beginGet();

        long finalGet = lifecycle.beginFinalGet();

        assertNotEquals(ClipboardSyncLifecycle.INVALID_TOKEN, finalGet);
        assertFalse(lifecycle.isActive(send));
        assertFalse(lifecycle.isActive(ordinaryGet));
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginSend());
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginGet());

        lifecycle.destroy();
        assertTrue(lifecycle.isActive(finalGet));

        lifecycle.finishGet(finalGet);
        assertFalse(lifecycle.isActive(finalGet));
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginGet());
    }

    @Test
    public void finalDisconnectGetCannotBeStartedTwice() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();

        long first = lifecycle.beginFinalGet();

        assertNotEquals(ClipboardSyncLifecycle.INVALID_TOKEN, first);
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginFinalGet());
    }

    @Test
    public void staleOrdinaryGetFinishCannotReopenDestroyedLifecycle() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();
        long get = lifecycle.beginGet();

        lifecycle.destroy();
        lifecycle.finishGet(get);

        assertFalse(lifecycle.isActive(get));
        assertEquals(ClipboardSyncLifecycle.INVALID_TOKEN, lifecycle.beginGet());
    }

    @Test
    public void runIfActiveRejectsStaleOrdinarySideEffects() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();
        long ordinaryGet = lifecycle.beginGet();
        AtomicBoolean ran = new AtomicBoolean();

        lifecycle.beginFinalGet();

        assertFalse(lifecycle.runIfActive(ordinaryGet, () -> ran.set(true)));
        assertFalse(ran.get());
    }

    @Test
    public void runIfActiveAllowsFinalCleanupAfterDestroy() {
        ClipboardSyncLifecycle lifecycle = new ClipboardSyncLifecycle();
        long finalGet = lifecycle.beginFinalGet();
        AtomicBoolean ran = new AtomicBoolean();

        lifecycle.destroy();

        assertTrue(lifecycle.runIfActive(finalGet, () -> ran.set(true)));
        assertTrue(ran.get());
    }
}
