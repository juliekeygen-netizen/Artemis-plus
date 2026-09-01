package com.limelight.nvstream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ConnectionPermitGateTest {
    @Test
    public void reconnectCanOnlyReuseCurrentOwner() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object owner = new Object();
        Object other = new Object();

        gate.acquire(owner);
        assertTrue(gate.canReuse(owner));
        assertFalse(gate.canReuse(other));
        assertTrue(gate.isOwnedBy(owner));
    }

    @Test
    public void ordinarySecondStartFromSameOwnerIsRejected() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object owner = new Object();
        gate.acquire(owner);

        try {
            gate.acquire(owner);
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

        gate.acquire(first);
        assertFalse(gate.release(second));
        assertTrue(gate.isOwnedBy(first));
        assertTrue(gate.release(first));
        assertFalse(gate.release(first));
        gate.acquire(second);
        assertTrue(gate.isOwnedBy(second));
    }

    @Test
    public void differentOwnerWaitsUntilCurrentOwnerReleases() throws Exception {
        ConnectionPermitGate gate = new ConnectionPermitGate();
        Object first = new Object();
        Object second = new Object();
        gate.acquire(first);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                gate.acquire(second);
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
        assertTrue(gate.isOwnedBy(second));
        waiter.join(1000);
    }
}
