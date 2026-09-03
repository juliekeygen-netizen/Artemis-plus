package com.limelight.nvstream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NvConnectionStartGateTest {
    @Test
    public void stopBeforePermitClaimDoesNotInventRelease() {
        NvConnectionStartGate gate = new NvConnectionStartGate();
        long token = gate.begin();

        assertNotEquals(NvConnectionStartGate.INVALID_TOKEN, token);
        assertFalse(gate.invalidateAndReleasePermit());
        assertFalse(gate.isActive(token));
        assertFalse(gate.claimPermit(token));

        long replacement = gate.begin();
        assertNotEquals(NvConnectionStartGate.INVALID_TOKEN, replacement);
        assertNotEquals(token, replacement);
    }

    @Test
    public void ownedPermitTransfersToStopExactlyOnce() {
        NvConnectionStartGate gate = new NvConnectionStartGate();
        long token = gate.begin();

        assertTrue(gate.claimPermit(token));
        gate.finishStart(token);
        assertTrue(gate.invalidateAndReleasePermit());
        assertFalse(gate.invalidateAndReleasePermit());
    }

    @Test
    public void duplicateStartRejectedWhileNegotiatingOrConnected() {
        NvConnectionStartGate gate = new NvConnectionStartGate();
        long token = gate.begin();

        assertEquals(NvConnectionStartGate.INVALID_TOKEN, gate.begin());
        assertTrue(gate.claimPermit(token));
        gate.finishStart(token);
        assertEquals(NvConnectionStartGate.INVALID_TOKEN, gate.begin());
    }

    @Test
    public void failedStartCanReleasePermitAndAllowRetryGeneration() {
        NvConnectionStartGate gate = new NvConnectionStartGate();
        long token = gate.begin();

        assertTrue(gate.claimPermit(token));
        assertTrue(gate.releasePermit(token));
        gate.finishStart(token);

        long retry = gate.begin();
        assertNotEquals(NvConnectionStartGate.INVALID_TOKEN, retry);
        assertNotEquals(token, retry);
        assertTrue(gate.isActive(retry));
    }
}
