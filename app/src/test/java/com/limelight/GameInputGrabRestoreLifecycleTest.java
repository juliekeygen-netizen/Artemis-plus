package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameInputGrabRestoreLifecycleTest {
    private static final String SOURCE_PATH = "src/main/java/com/limelight/Game.java";

    @Test
    public void automaticRestoreRevalidatesForegroundLifecycleState() throws IOException {
        String source = readSource();
        String runnable = between(source,
                "private final Runnable restoreInputGrabRunnable",
                "private void scheduleInputGrabRestore(long delayMs)");

        assertTrue(runnable.contains("inputCallbacksDestroyed"));
        assertTrue(runnable.contains("isFinishing()"));
        assertTrue(runnable.contains("isDestroyed()"));
        assertTrue(runnable.contains("!connected"));
        assertTrue(runnable.contains("keepAliveLifecycleArmed"));
        assertTrue(runnable.contains("keepAliveBackgrounded"));
        assertTrue(runnable.contains("keepAliveReturnPending"));
        assertTrue(runnable.contains("fastResumeLifecycleArmed"));
        assertTrue(runnable.contains("fastResumeBackgrounded"));
        assertTrue(runnable.contains("fastResumeReconnectPending"));
        assertTrue(runnable.contains("setInputGrabState(true);"));
    }

    @Test
    public void automaticRestoreIsCoalescedOnOwnedHandler() throws IOException {
        String source = readSource();
        String scheduler = between(source,
                "private void scheduleInputGrabRestore(long delayMs)",
                "private final Runnable finishSecondScreenRunnable");

        int cancel = scheduler.indexOf("timerHandler.removeCallbacks(restoreInputGrabRunnable);");
        int schedule = scheduler.indexOf("timerHandler.postDelayed(restoreInputGrabRunnable, delayMs);");

        assertTrue(scheduler.contains("timerHandler == null || inputCallbacksDestroyed"));
        assertTrue(cancel >= 0);
        assertTrue(schedule > cancel);
    }

    @Test
    public void explicitUngrabCancelsOlderAutomaticRestore() throws IOException {
        String source = readSource();
        String setter = between(source,
                "private void setInputGrabState(boolean grab)",
                "private final Runnable toggleGrab");

        int ungrab = setter.indexOf("if (!grab && timerHandler != null)");
        int cancel = setter.indexOf("timerHandler.removeCallbacks(restoreInputGrabRunnable);");
        int disableCapture = setter.indexOf("inputCaptureProvider.disableCapture();");

        assertTrue(ungrab >= 0);
        assertTrue(cancel > ungrab);
        assertTrue(disableCapture > cancel);
    }

    @Test
    public void foregroundTransitionsUseOwnedRestoreInsteadOfAnonymousCallbacks() throws IOException {
        String source = readSource();
        String keepAliveReturn = between(source,
                "private boolean restoreKeepAliveVisibleSurfaceIfReady()",
                "private void releaseStreamingWifiLocks()");
        String connectionStarted = between(source,
                "public void connectionStarted()",
                "public void displayMessage(final String message)");

        assertTrue(keepAliveReturn.contains("scheduleInputGrabRestore(300);"));
        assertFalse(keepAliveReturn.contains("timerHandler.postDelayed(() ->"));

        assertTrue(connectionStarted.contains("scheduleInputGrabRestore(500);"));
        assertFalse(connectionStarted.contains("timerHandler.postDelayed(new Runnable()"));
    }

    private static String readSource() throws IOException {
        Path path = Paths.get(SOURCE_PATH);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("Missing start marker: " + startMarker, start >= 0);
        assertTrue("Missing end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }
}
