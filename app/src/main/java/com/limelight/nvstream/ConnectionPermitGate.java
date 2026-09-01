package com.limelight.nvstream;

/**
 * Process-wide single-connection ownership gate.
 *
 * A live NvConnection keeps ownership across smart-reconnect starts so reconnect can rebuild the
 * native transport without deadlocking on the permit it already owns. Final stop releases exactly
 * once. A different NvConnection waits until the current owner releases the gate.
 */
final class ConnectionPermitGate {
    private Object owner;

    /** Acquire previously-free connection ownership, waiting for another owner to stop. */
    synchronized void acquire(Object claimant) throws InterruptedException {
        if (claimant == null) {
            throw new IllegalArgumentException("claimant must not be null");
        }
        if (owner == claimant) {
            throw new IllegalStateException("Connection already owns the global permit");
        }
        while (owner != null) {
            wait();
            if (owner == claimant) {
                throw new IllegalStateException("Connection already owns the global permit");
            }
        }
        owner = claimant;
    }

    /** Reconnect may reuse the permit only when this exact connection already owns it. */
    synchronized boolean canReuse(Object claimant) {
        return owner == claimant;
    }

    synchronized boolean isOwnedBy(Object claimant) {
        return owner == claimant;
    }

    /** Release only when the caller is the current owner. Repeated/stale stops are harmless. */
    synchronized boolean release(Object claimant) {
        if (owner != claimant) {
            return false;
        }
        owner = null;
        notifyAll();
        return true;
    }
}
