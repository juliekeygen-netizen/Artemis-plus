package com.limelight;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generation fence for asynchronous smart-reconnect attempts.
 *
 * Starting a newer attempt or cancelling reconnect work immediately invalidates every older token,
 * allowing background retry threads to stop before they can mutate a newer/destroyed Game session.
 */
final class SmartReconnectFence {
    private final AtomicInteger generation = new AtomicInteger();

    int beginAttempt() {
        return generation.incrementAndGet();
    }

    void cancel() {
        generation.incrementAndGet();
    }

    boolean isCurrent(int token) {
        return generation.get() == token;
    }
}
