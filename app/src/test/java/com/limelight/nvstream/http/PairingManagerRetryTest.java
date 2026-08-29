package com.limelight.nvstream.http;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;

public class PairingManagerRetryTest {
    @Test
    public void retriesPrematureEndOfStream() {
        assertTrue(PairingManager.isRetryableInitialPairingDisconnect(
                new ProtocolException("unexpected end of stream on http://192.168.1.10:47989/...")));
    }

    @Test
    public void retriesEofAndConnectionReset() {
        assertTrue(PairingManager.isRetryableInitialPairingDisconnect(new EOFException("EOF")));
        assertTrue(PairingManager.isRetryableInitialPairingDisconnect(new SocketException("Connection reset")));
    }

    @Test
    public void retriesNestedPrematureEof() {
        IOException wrapper = new IOException("wrapper", new EOFException("peer closed response"));
        assertTrue(PairingManager.isRetryableInitialPairingDisconnect(wrapper));
    }

    @Test
    public void doesNotRetryNormalNetworkOrServerErrors() {
        assertFalse(PairingManager.isRetryableInitialPairingDisconnect(
                new IOException("Network is unreachable")));
        assertFalse(PairingManager.isRetryableInitialPairingDisconnect(
                new HostHttpResponseException(401, "Unauthorized")));
    }
}
