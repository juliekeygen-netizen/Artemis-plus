package com.limelight;

/**
 * Generation fence and single-owner gate for asynchronous smart-reconnect sequences.
 *
 * A reconnect sequence owns one token until it succeeds, exhausts its retry budget, or is
 * cancelled. Termination callbacks emitted by transports started inside that sequence can detect
 * the existing owner instead of recursively starting another retry worker.
 */
final class SmartReconnectFence {
    static final int NO_ATTEMPT = 0;

    private int generation;
    private int activeToken = NO_ATTEMPT;

    synchronized int beginAttempt() {
        activeToken = nextGeneration();
        return activeToken;
    }

    synchronized int beginAttemptIfIdle() {
        if (activeToken != NO_ATTEMPT) {
            return NO_ATTEMPT;
        }
        return beginAttempt();
    }

    synchronized boolean hasActiveAttempt() {
        return activeToken != NO_ATTEMPT;
    }

    synchronized boolean complete(int token) {
        if (!isCurrent(token)) {
            return false;
        }
        activeToken = NO_ATTEMPT;
        nextGeneration();
        return true;
    }

    synchronized void completeActive() {
        if (activeToken != NO_ATTEMPT) {
            activeToken = NO_ATTEMPT;
            nextGeneration();
        }
    }

    synchronized void cancel() {
        activeToken = NO_ATTEMPT;
        nextGeneration();
    }

    synchronized boolean isCurrent(int token) {
        return token != NO_ATTEMPT && activeToken == token && generation == token;
    }

    private int nextGeneration() {
        generation++;
        // Zero is reserved as the explicit "no active attempt" sentinel. Integer overflow is
        // fantastically unlikely here, but skipping zero keeps the state machine correct anyway.
        if (generation == NO_ATTEMPT) {
            generation++;
        }
        return generation;
    }
}
