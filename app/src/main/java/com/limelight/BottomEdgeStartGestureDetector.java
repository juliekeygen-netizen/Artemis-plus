package com.limelight;

/** Pure gesture decision logic used before forwarding bottom-edge touch events to Windows. */
public final class BottomEdgeStartGestureDetector {
    public static final String MODE_NATIVE = "native";
    public static final String MODE_WINDOWS_KEY = "windows_key";
    public static final String MODE_DISABLED = "disabled";

    public enum Decision {
        PENDING,
        TRIGGER,
        FALLBACK,
        CANCEL
    }

    private final float edgeHeightPx;
    private final float triggerDistancePx;
    private final float maxHorizontalDriftPx;
    private final long decisionTimeoutMs;
    private boolean consumeUntilTerminal;

    public BottomEdgeStartGestureDetector(float density) {
        float safeDensity = Math.max(0.5f, density);
        edgeHeightPx = 32f * safeDensity;
        triggerDistancePx = 48f * safeDensity;
        maxHorizontalDriftPx = 40f * safeDensity;
        decisionTimeoutMs = 220L;
    }

    public boolean startsInBottomEdge(float y, float viewHeight) {
        return viewHeight > 0 && y >= Math.max(0f, viewHeight - edgeHeightPx);
    }

    public long getDecisionTimeoutMs() {
        return decisionTimeoutMs;
    }

    public void consumeRecognizedGestureUntilTerminal() {
        consumeUntilTerminal = true;
    }

    public boolean shouldConsumeRecognizedGestureEvent(boolean terminalEvent) {
        if (!consumeUntilTerminal) {
            return false;
        }
        if (terminalEvent) {
            consumeUntilTerminal = false;
        }
        return true;
    }

    public void resetRecognizedGestureConsumption() {
        consumeUntilTerminal = false;
    }

    public Decision decide(float deltaX, float upwardDeltaY, long elapsedMs,
                           int pointerCount, boolean isUp, boolean isCancel) {
        if (isCancel) {
            return Decision.CANCEL;
        }
        if (pointerCount != 1) {
            return Decision.FALLBACK;
        }
        if (upwardDeltaY >= triggerDistancePx && Math.abs(deltaX) <= maxHorizontalDriftPx) {
            return Decision.TRIGGER;
        }
        if (Math.abs(deltaX) > maxHorizontalDriftPx || upwardDeltaY < -(triggerDistancePx * 0.5f)) {
            return Decision.FALLBACK;
        }
        if (isUp || elapsedMs >= decisionTimeoutMs) {
            return Decision.FALLBACK;
        }
        return Decision.PENDING;
    }
}
