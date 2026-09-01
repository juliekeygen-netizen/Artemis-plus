package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartReconnectFenceTest {
    @Test
    public void newestAttemptInvalidatesOlderAttempt() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int first = fence.beginAttempt();
        assertTrue(fence.isCurrent(first));

        int second = fence.beginAttempt();
        assertFalse(fence.isCurrent(first));
        assertTrue(fence.isCurrent(second));
    }

    @Test
    public void idleBeginRejectsNestedReconnectSequence() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int owner = fence.beginAttemptIfIdle();

        assertTrue(owner != SmartReconnectFence.NO_ATTEMPT);
        assertTrue(fence.hasActiveAttempt());
        assertTrue(fence.isCurrent(owner));
        assertEquals(SmartReconnectFence.NO_ATTEMPT, fence.beginAttemptIfIdle());
        assertTrue(fence.isCurrent(owner));
    }

    @Test
    public void completeReleasesOwnershipAndInvalidatesWorker() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int owner = fence.beginAttemptIfIdle();

        assertTrue(fence.complete(owner));
        assertFalse(fence.hasActiveAttempt());
        assertFalse(fence.isCurrent(owner));

        int next = fence.beginAttemptIfIdle();
        assertTrue(next != SmartReconnectFence.NO_ATTEMPT);
        assertTrue(fence.isCurrent(next));
    }

    @Test
    public void staleWorkerCannotCompleteNewOwner() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int first = fence.beginAttempt();
        int second = fence.beginAttempt();

        assertFalse(fence.complete(first));
        assertTrue(fence.isCurrent(second));
        assertTrue(fence.hasActiveAttempt());
    }

    @Test
    public void completeActiveInvalidatesCurrentWorker() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int owner = fence.beginAttemptIfIdle();

        fence.completeActive();

        assertFalse(fence.isCurrent(owner));
        assertFalse(fence.hasActiveAttempt());
    }

    @Test
    public void cancelInvalidatesCurrentAttempt() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int token = fence.beginAttempt();
        assertTrue(fence.isCurrent(token));

        fence.cancel();
        assertFalse(fence.isCurrent(token));
        assertFalse(fence.hasActiveAttempt());
    }
}
