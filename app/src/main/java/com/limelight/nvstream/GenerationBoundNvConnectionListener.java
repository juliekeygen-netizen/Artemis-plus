package com.limelight.nvstream;

/**
 * Drops callbacks from an NvConnection start generation after stop() invalidates that generation.
 * This prevents slow HTTP/native startup work from mutating a later stream session.
 */
final class GenerationBoundNvConnectionListener implements NvConnectionListener {
    private final NvConnectionListener delegate;
    private final NvConnectionStartGate startGate;
    private final long token;

    GenerationBoundNvConnectionListener(NvConnectionListener delegate,
                                        NvConnectionStartGate startGate,
                                        long token) {
        this.delegate = delegate;
        this.startGate = startGate;
        this.token = token;
    }

    private boolean isActive() {
        return startGate.isActive(token);
    }

    @Override
    public void stageStarting(String stage) {
        if (isActive()) delegate.stageStarting(stage);
    }

    @Override
    public void stageComplete(String stage) {
        if (isActive()) delegate.stageComplete(stage);
    }

    @Override
    public boolean stageFailed(String stage, int portFlags, int errorCode) {
        return isActive() && delegate.stageFailed(stage, portFlags, errorCode);
    }

    @Override
    public void connectionStarted() {
        if (isActive()) delegate.connectionStarted();
    }

    @Override
    public void connectionTerminated(int errorCode) {
        if (isActive()) delegate.connectionTerminated(errorCode);
    }

    @Override
    public void connectionStatusUpdate(int connectionStatus) {
        if (isActive()) delegate.connectionStatusUpdate(connectionStatus);
    }

    @Override
    public void displayMessage(String message) {
        if (isActive()) delegate.displayMessage(message);
    }

    @Override
    public void displayTransientMessage(String message) {
        if (isActive()) delegate.displayTransientMessage(message);
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        if (isActive()) delegate.rumble(controllerNumber, lowFreqMotor, highFreqMotor);
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        if (isActive()) delegate.rumbleTriggers(controllerNumber, leftTrigger, rightTrigger);
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        if (isActive()) delegate.setHdrMode(enabled, hdrMetadata);
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
        if (isActive()) delegate.setMotionEventState(controllerNumber, motionType, reportRateHz);
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
        if (isActive()) delegate.setControllerLED(controllerNumber, r, g, b);
    }
}
