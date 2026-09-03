package com.limelight;

/**
 * Owns the lifetime of smart-reconnect work associated with one Game Activity.
 *
 * The gate deliberately contains no Android dependencies so its race/teardown invariants can be
 * regression-tested directly. Exactly one reconnect generation may own retry work at a time.
 * Successful recovery releases that ownership for a later outage, while a terminal failure closes
 * the current stream session and Activity destruction permanently invalidates all outstanding work.
 */
final class SmartReconnectLifecycle {
    static final long INVALID_TOKEN = -1L;

    private long generation;
    private boolean reconnectActive;
    private boolean terminal;
    private boolean destroyed;

    synchronized long tryBegin() {
        if (destroyed || terminal || reconnectActive) {
            return INVALID_TOKEN;
        }

        reconnectActive = true;
        return ++generation;
    }

    synchronized boolean hasActiveReconnect() {
        return !destroyed && !terminal && reconnectActive;
    }

    synchronized boolean isActive(long token) {
        return token != INVALID_TOKEN && !destroyed && !terminal &&
                reconnectActive && token == generation;
    }

    synchronized boolean finishSuccess(long token) {
        if (!isActive(token)) {
            return false;
        }

        reconnectActive = false;
        return true;
    }

    synchronized boolean finishFailure(long token) {
        if (!isActive(token)) {
            return false;
        }

        reconnectActive = false;
        terminal = true;
        generation++;
        return true;
    }

    /**
     * Closes the stream session for a non-reconnectable termination. If a reconnect generation won
     * a race just before this call, its token is invalidated so it cannot restart the connection.
     */
    synchronized boolean terminateSession() {
        if (destroyed || terminal) {
            return false;
        }

        terminal = true;
        reconnectActive = false;
        generation++;
        return true;
    }

    synchronized boolean isTerminal() {
        return terminal;
    }

    synchronized boolean isDestroyed() {
        return destroyed;
    }

    synchronized void destroy() {
        destroyed = true;
        reconnectActive = false;
        generation++;
    }
}
