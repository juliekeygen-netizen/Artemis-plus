package com.limelight.nvstream;

/**
 * Tracks asynchronous NvConnection start ownership independently from MoonBridge.
 *
 * A start token remains active until stop() invalidates it or a newer accepted start begins. The
 * global MoonBridge semaphore is represented separately as a permit owner so stop() can release a
 * permit only when this connection generation actually acquired one.
 */
final class NvConnectionStartGate {
    static final long INVALID_TOKEN = -1L;

    private long generation;
    private boolean startInFlight;
    private long permitOwner = INVALID_TOKEN;

    synchronized long begin() {
        // A second start while another start is still negotiating, or while this connection owns
        // the global MoonBridge permit, would create overlapping connection state.
        if (startInFlight || permitOwner != INVALID_TOKEN) {
            return INVALID_TOKEN;
        }

        startInFlight = true;
        return ++generation;
    }

    synchronized boolean isActive(long token) {
        return token != INVALID_TOKEN && token == generation;
    }

    synchronized boolean claimPermit(long token) {
        if (!isActive(token) || !startInFlight || permitOwner != INVALID_TOKEN) {
            return false;
        }

        permitOwner = token;
        return true;
    }

    synchronized boolean releasePermit(long token) {
        if (permitOwner != token) {
            return false;
        }

        permitOwner = INVALID_TOKEN;
        return true;
    }

    /**
     * Invalidates all callbacks/work from the current start generation and transfers any owned
     * semaphore permit to the caller for release after native stop/cleanup completes.
     */
    synchronized boolean invalidateAndReleasePermit() {
        generation++;
        startInFlight = false;

        boolean hadPermit = permitOwner != INVALID_TOKEN;
        permitOwner = INVALID_TOKEN;
        return hadPermit;
    }

    synchronized void finishStart(long token) {
        if (isActive(token)) {
            startInFlight = false;
        }
    }
}
