package com.limelight;

/**
 * Generation gate for Activity-owned smart reconnect work. Tokens from older attempts become
 * invalid as soon as a newer retry starts, the retry is cancelled, or the owning Activity dies.
 */
final class SmartReconnectAttemptGuard {
    static final long NO_ATTEMPT = 0L;

    private long generation;
    private boolean destroyed;

    synchronized long beginAttempt() {
        if (destroyed) {
            return NO_ATTEMPT;
        }
        return ++generation;
    }

    synchronized void cancelActiveAttempt() {
        generation++;
    }

    synchronized void destroy() {
        destroyed = true;
        generation++;
    }

    synchronized boolean isAttemptActive(long token) {
        return !destroyed && token != NO_ATTEMPT && token == generation;
    }

    synchronized boolean isDestroyed() {
        return destroyed;
    }
}
