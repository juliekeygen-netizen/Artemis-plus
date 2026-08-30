package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BottomEdgeStartGestureDetectorTest {
    private final BottomEdgeStartGestureDetector detector = new BottomEdgeStartGestureDetector(1f);

    @Test
    public void onlyStartsInsideBottomBand() {
        assertTrue(detector.startsInBottomEdge(980f, 1000f));
        assertFalse(detector.startsInBottomEdge(900f, 1000f));
    }

    @Test
    public void upwardSwipeTriggersButHorizontalOrMultitouchFallsBack() {
        assertEquals(BottomEdgeStartGestureDetector.Decision.TRIGGER,
                detector.decide(10f, 60f, 80, 1, false, false));
        assertEquals(BottomEdgeStartGestureDetector.Decision.FALLBACK,
                detector.decide(60f, 60f, 80, 1, false, false));
        assertEquals(BottomEdgeStartGestureDetector.Decision.FALLBACK,
                detector.decide(0f, 20f, 50, 2, false, false));
    }

    @Test
    public void recognizedGestureConsumesEventsThroughTerminalOnly() {
        detector.consumeRecognizedGestureUntilTerminal();
        assertTrue(detector.shouldConsumeRecognizedGestureEvent(false));
        assertTrue(detector.shouldConsumeRecognizedGestureEvent(true));
        assertFalse(detector.shouldConsumeRecognizedGestureEvent(false));
    }

    @Test
    public void ordinaryTapFallsBackAndAndroidCancelIsDiscarded() {
        assertEquals(BottomEdgeStartGestureDetector.Decision.FALLBACK,
                detector.decide(0f, 2f, 90, 1, true, false));
        assertEquals(BottomEdgeStartGestureDetector.Decision.CANCEL,
                detector.decide(0f, 4f, 40, 1, false, true));
        assertEquals(BottomEdgeStartGestureDetector.Decision.FALLBACK,
                detector.decide(0f, 10f, detector.getDecisionTimeoutMs(), 1, false, false));
    }
}
