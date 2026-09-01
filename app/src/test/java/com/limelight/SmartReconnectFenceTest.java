package com.limelight;

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
    public void cancelInvalidatesCurrentAttempt() {
        SmartReconnectFence fence = new SmartReconnectFence();
        int token = fence.beginAttempt();
        assertTrue(fence.isCurrent(token));

        fence.cancel();
        assertFalse(fence.isCurrent(token));
    }
}
