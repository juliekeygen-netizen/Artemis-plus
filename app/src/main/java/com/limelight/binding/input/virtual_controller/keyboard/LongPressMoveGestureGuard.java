package com.limelight.binding.input.virtual_controller.keyboard;

/**
 * Tracks whether a hold-to-move gesture stopped being a valid long press before the move timer
 * armed. This prevents an ordinary wandering hold from unexpectedly becoming a drag later.
 */
final class LongPressMoveGestureGuard {
    private boolean disqualified;

    void reset() {
        disqualified = false;
    }

    /**
     * @return true when pending long-press timers must be cancelled.
     */
    boolean onMovePastSlop(boolean moveArmed) {
        if (!moveArmed) {
            disqualified = true;
            return true;
        }
        return false;
    }

    boolean canPerformClick(boolean moved, boolean moveArmed, boolean resetPromptShown) {
        return !disqualified && !moved && !moveArmed && !resetPromptShown;
    }

    boolean isDisqualified() {
        return disqualified;
    }
}
