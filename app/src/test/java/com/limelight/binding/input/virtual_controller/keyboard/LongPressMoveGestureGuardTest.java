package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LongPressMoveGestureGuardTest {
    @Test
    public void movementBeforeArmCancelsLongPressAndClick() {
        LongPressMoveGestureGuard guard = new LongPressMoveGestureGuard();
        guard.reset();

        assertTrue(guard.onMovePastSlop(false));
        assertTrue(guard.isDisqualified());
        assertFalse(guard.canPerformClick(false, false, false));
    }

    @Test
    public void movementAfterArmRemainsAValidDrag() {
        LongPressMoveGestureGuard guard = new LongPressMoveGestureGuard();
        guard.reset();

        assertFalse(guard.onMovePastSlop(true));
        assertFalse(guard.isDisqualified());
        assertFalse(guard.canPerformClick(true, true, false));
    }

    @Test
    public void ordinaryTapStillClicks() {
        LongPressMoveGestureGuard guard = new LongPressMoveGestureGuard();
        guard.reset();

        assertTrue(guard.canPerformClick(false, false, false));
    }
}
