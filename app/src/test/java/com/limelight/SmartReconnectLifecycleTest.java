package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartReconnectLifecycleTest {
    @Test
    public void onlyOneReconnectGenerationCanOwnWorkAtATime() {
        SmartReconnectLifecycle lifecycle = new SmartReconnectLifecycle();

        long first = lifecycle.tryBegin();
        assertNotEquals(SmartReconnectLifecycle.INVALID_TOKEN, first);
        assertTrue(lifecycle.hasActiveReconnect());
        assertTrue(lifecycle.isActive(first));
        assertEquals(SmartReconnectLifecycle.INVALID_TOKEN, lifecycle.tryBegin());

        assertTrue(lifecycle.finishSuccess(first));
        assertFalse(lifecycle.hasActiveReconnect());
        assertFalse(lifecycle.isActive(first));

        long second = lifecycle.tryBegin();
        assertNotEquals(SmartReconnectLifecycle.INVALID_TOKEN, second);
        assertNotEquals(first, second);
        assertTrue(lifecycle.isActive(second));
    }

    @Test
    public void staleGenerationCannotCompleteNewReconnectOwner() {
        SmartReconnectLifecycle lifecycle = new SmartReconnectLifecycle();
        long first = lifecycle.tryBegin();
        assertTrue(lifecycle.finishSuccess(first));

        long second = lifecycle.tryBegin();
        assertFalse(lifecycle.finishSuccess(first));
        assertTrue(lifecycle.isActive(second));
        assertTrue(lifecycle.finishSuccess(second));
    }

    @Test
    public void terminalReconnectFailureBlocksLaterReconnectWorkers() {
        SmartReconnectLifecycle lifecycle = new SmartReconnectLifecycle();
        long token = lifecycle.tryBegin();

        assertTrue(lifecycle.finishFailure(token));

        assertTrue(lifecycle.isTerminal());
        assertFalse(lifecycle.hasActiveReconnect());
        assertFalse(lifecycle.isActive(token));
        assertEquals(SmartReconnectLifecycle.INVALID_TOKEN, lifecycle.tryBegin());
    }

    @Test
    public void nonReconnectableTerminationInvalidatesRacingReconnectOwner() {
        SmartReconnectLifecycle lifecycle = new SmartReconnectLifecycle();
        long token = lifecycle.tryBegin();

        assertTrue(lifecycle.terminateSession());

        assertTrue(lifecycle.isTerminal());
        assertFalse(lifecycle.isActive(token));
        assertFalse(lifecycle.finishSuccess(token));
        assertEquals(SmartReconnectLifecycle.INVALID_TOKEN, lifecycle.tryBegin());
    }

    @Test
    public void destroyPermanentlyInvalidatesReconnectWork() {
        SmartReconnectLifecycle lifecycle = new SmartReconnectLifecycle();
        long token = lifecycle.tryBegin();

        lifecycle.destroy();

        assertTrue(lifecycle.isDestroyed());
        assertFalse(lifecycle.hasActiveReconnect());
        assertFalse(lifecycle.isActive(token));
        assertFalse(lifecycle.finishSuccess(token));
        assertEquals(SmartReconnectLifecycle.INVALID_TOKEN, lifecycle.tryBegin());
    }
}
