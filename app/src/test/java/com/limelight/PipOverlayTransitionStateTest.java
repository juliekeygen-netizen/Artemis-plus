package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PipOverlayTransitionStateTest {
    @Test
    public void repeatedEnterCannotOverwriteOriginalSnapshot() {
        PipOverlayTransitionState state = new PipOverlayTransitionState();
        assertTrue(state.enter(true, true, true, false, true, true, 0, true));
        assertFalse(state.enter(false, false, false, false, false, false, 8, false));
        assertTrue(state.floatingButtonShown);
        assertTrue(state.zoomButtonShown);
        assertTrue(state.virtualControllerShown);
        assertTrue(state.keyboardLayoutShown);
        assertTrue(state.performanceOverlayShown);
        assertTrue(state.statsOverlayShown);
    }

    @Test
    public void exitOnlyRunsForARealTransition() {
        PipOverlayTransitionState state = new PipOverlayTransitionState();
        assertFalse(state.exit());
        assertTrue(state.enter(false, false, false, false, false, false, 8, false));
        assertTrue(state.exit());
        assertFalse(state.exit());
    }
}
