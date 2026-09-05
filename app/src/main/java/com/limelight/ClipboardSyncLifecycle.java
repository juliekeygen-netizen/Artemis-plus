package com.limelight;

/**
 * Owns clipboard synchronization work for a single Game Activity lifetime.
 *
 * <p>Ordinary sends may overlap, while ordinary clipboard fetches are coalesced so only one GET
 * can be in flight at a time. A final disconnect fetch becomes the terminal lifecycle owner: it
 * invalidates ordinary work, survives Activity destruction long enough to copy the host clipboard,
 * and rejects any later ordinary clipboard work.</p>
 */
final class ClipboardSyncLifecycle {
    static final long INVALID_TOKEN = -1L;

    private long generation = 1L;
    private long activeGetToken = INVALID_TOKEN;
    private long finalGetToken = INVALID_TOKEN;
    private boolean finishing;
    private boolean destroyed;

    synchronized long beginSend() {
        return destroyed || finishing ? INVALID_TOKEN : generation;
    }

    synchronized long beginGet() {
        if (destroyed || finishing || activeGetToken != INVALID_TOKEN) {
            return INVALID_TOKEN;
        }

        activeGetToken = generation;
        return generation;
    }

    synchronized long beginFinalGet() {
        if (destroyed || finishing || finalGetToken != INVALID_TOKEN) {
            return INVALID_TOKEN;
        }

        finishing = true;
        generation++;
        activeGetToken = INVALID_TOKEN;
        finalGetToken = generation;
        return finalGetToken;
    }

    synchronized boolean isActive(long token) {
        return isActiveLocked(token);
    }

    synchronized boolean runIfActive(long token, Runnable action) {
        if (!isActiveLocked(token)) {
            return false;
        }
        action.run();
        return true;
    }

    synchronized void finishGet(long token) {
        if (activeGetToken == token) {
            activeGetToken = INVALID_TOKEN;
        }
        if (finalGetToken == token) {
            finalGetToken = INVALID_TOKEN;
        }
    }

    synchronized void destroy() {
        if (!destroyed) {
            destroyed = true;
            generation++;
            activeGetToken = INVALID_TOKEN;
        }
    }

    private boolean isActiveLocked(long token) {
        if (token == INVALID_TOKEN) {
            return false;
        }
        if (token == finalGetToken) {
            return true;
        }
        return !destroyed && !finishing && token == generation;
    }
}
