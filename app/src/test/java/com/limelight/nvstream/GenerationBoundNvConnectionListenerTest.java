package com.limelight.nvstream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GenerationBoundNvConnectionListenerTest {
    @Test
    public void activeGenerationDelegatesAndStaleGenerationIsSuppressed() {
        NvConnectionStartGate gate = new NvConnectionStartGate();
        long token = gate.begin();
        RecordingListener delegate = new RecordingListener();
        GenerationBoundNvConnectionListener listener =
                new GenerationBoundNvConnectionListener(delegate, gate, token);

        listener.stageStarting("video");
        listener.connectionStarted();
        assertTrue(listener.stageFailed("video", 1, 2));
        assertEquals(3, delegate.callbackCount);

        gate.invalidateAndReleasePermit();

        listener.stageComplete("video");
        listener.connectionTerminated(3);
        listener.displayMessage("stale");
        assertFalse(listener.stageFailed("video", 1, 2));
        assertEquals(3, delegate.callbackCount);
    }

    private static final class RecordingListener implements NvConnectionListener {
        int callbackCount;

        @Override public void stageStarting(String stage) { callbackCount++; }
        @Override public void stageComplete(String stage) { callbackCount++; }
        @Override public boolean stageFailed(String stage, int portFlags, int errorCode) {
            callbackCount++;
            return true;
        }
        @Override public void connectionStarted() { callbackCount++; }
        @Override public void connectionTerminated(int errorCode) { callbackCount++; }
        @Override public void connectionStatusUpdate(int connectionStatus) { callbackCount++; }
        @Override public void displayMessage(String message) { callbackCount++; }
        @Override public void displayTransientMessage(String message) { callbackCount++; }
        @Override public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) { callbackCount++; }
        @Override public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) { callbackCount++; }
        @Override public void setHdrMode(boolean enabled, byte[] hdrMetadata) { callbackCount++; }
        @Override public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) { callbackCount++; }
        @Override public void setControllerLED(short controllerNumber, byte r, byte g, byte b) { callbackCount++; }
    }
}
