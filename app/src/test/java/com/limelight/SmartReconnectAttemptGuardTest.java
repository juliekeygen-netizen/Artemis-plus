package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartReconnectAttemptGuardTest {
    @Test
    public void newerAttemptInvalidatesOlderToken() {
        SmartReconnectAttemptGuard guard = new SmartReconnectAttemptGuard();
        long first = guard.beginAttempt();
        long second = guard.beginAttempt();
        assertFalse(guard.isAttemptActive(first));
        assertTrue(guard.isAttemptActive(second));
    }

    @Test
    public void cancellationInvalidatesCurrentAttemptButAllowsFutureRetry() {
        SmartReconnectAttemptGuard guard = new SmartReconnectAttemptGuard();
        long first = guard.beginAttempt();
        guard.cancelActiveAttempt();
        long second = guard.beginAttempt();
        assertFalse(guard.isAttemptActive(first));
        assertTrue(guard.isAttemptActive(second));
    }

    @Test
    public void destructionPermanentlyRejectsReconnectWork() {
        SmartReconnectAttemptGuard guard = new SmartReconnectAttemptGuard();
        long active = guard.beginAttempt();
        guard.destroy();
        assertTrue(guard.isDestroyed());
        assertFalse(guard.isAttemptActive(active));
        assertEquals(SmartReconnectAttemptGuard.NO_ATTEMPT, guard.beginAttempt());
    }
}
