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

    /**
     * Acquire connection ownership.
     *
     * @return true when this call acquired previously-free ownership, false when an existing owner
     *         was deliberately reused for reconnect.
     */
    synchronized boolean acquire(Object claimant, boolean allowOwnerReuse) throws InterruptedException {
        if (claimant == null) {
            throw new IllegalArgumentException("claimant must not be null");
        }

        if (owner == claimant) {
            if (!allowOwnerReuse) {
                throw new IllegalStateException("Connection already owns the global permit");
            }
            return false;
        }

        while (owner != null) {
            wait();
            if (owner == claimant) {
                if (!allowOwnerReuse) {
                    throw new IllegalStateException("Connection already owns the global permit");
                }
                return false;
            }
        }

        owner = claimant;
        return true;
    }

    synchronized boolean isOwnedBy(Object claimant) {
        return owner == claimant;
    }

    /**
     * Release only when the caller is the current owner. Repeated/stale stop paths are harmless.
     */
    synchronized boolean release(Object claimant) {
        if (owner != claimant) {
            return false;
        }
        owner = null;
        notifyAll();
        return true;
    }
}
