package com.limelight;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PipManualEntryGuardTest {
    @Test
    public void returnsTrueWhenEntrySucceeds() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertTrue(PipManualEntryGuard.tryEnter(() -> invoked.set(true)));
        assertTrue(invoked.get());
    }

    @Test
    public void returnsFalseWhenEntryThrows() {
        assertFalse(PipManualEntryGuard.tryEnter(() -> {
            throw new IllegalStateException("OEM PiP failure");
        }));
    }
}
