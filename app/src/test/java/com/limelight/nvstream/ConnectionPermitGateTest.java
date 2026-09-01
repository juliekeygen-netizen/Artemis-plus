package com.limelight.nvstream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class ConnectionPermitGateTest {
    @Test
    public void reconnectCanReuseCurrentOwnerWithoutReleasing() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object owner = new Object();

        assertTrue(gate.acquire(owner, false));
        assertFalse(gate.acquire(owner, true));
        assertTrue(gate.isOwnedBy(owner));
    }

    @Test
    public void ordinarySecondStartFromSameOwnerIsRejected() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object owner = new Object();
        gate.acquire(owner, false);

        try {
            gate.acquire(owner, false);
            fail("Expected duplicate ordinary start to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(gate.isOwnedBy(owner));
        }
    }

    @Test
    public void staleOrRepeatedReleaseCannotFreeAnotherOwner() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object first = new Object();
        Object second = new Object();

        gate.acquire(first, false);
        assertFalse(gate.release(second));
        assertTrue(gate.isOwnedBy(first));
        assertTrue(gate.release(first));
        assertFalse(gate.release(first));
        assertTrue(gate.acquire(second, false));
        assertTrue(gate.isOwnedBy(second));
    }

    @Test
    public void differentOwnerWaitsUntilCurrentOwnerReleases() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object first = new Object();
        Object second = new Object();
        gate.acquire(first, false);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);
        AtomicBoolean acquiredFresh = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                acquiredFresh.set(gate.acquire(second, false));
                acquired.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertFalse(acquired.await(100, TimeUnit.MILLISECONDS));
        assertTrue(gate.release(first));
        assertTrue(acquired.await(1, TimeUnit.SECONDS));
        assertTrue(acquiredFresh.get());
        assertTrue(gate.isOwnedBy(second));
        waiter.join(1000);
    }
}
