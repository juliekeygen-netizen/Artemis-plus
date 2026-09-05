package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameConnectionCallbackLifecycleTest {
    private static final String SOURCE_PATH = "src/main/java/com/limelight/Game.java";

    @Test
    public void queuedUiCallbacksRevalidateActivityOwnershipOnMainThread() throws IOException {
        String source = readSource();
        String ownerCheck = between(source,
                "private boolean shouldIgnoreGameUiCallback()",
                "private void runOnUiThreadIfActive(Runnable runnable)");
        String dispatcher = between(source,
                "private void runOnUiThreadIfActive(Runnable runnable)",
                "private final Runnable restoreInputGrabRunnable");

        assertTrue(ownerCheck.contains("inputCallbacksDestroyed || isFinishing() || isDestroyed()"));
        assertTrue(dispatcher.contains("if (inputCallbacksDestroyed)"));
        assertTrue(dispatcher.contains("runOnUiThread(() ->"));
        assertTrue(dispatcher.contains("if (shouldIgnoreGameUiCallback())"));
        assertTrue(dispatcher.indexOf("if (shouldIgnoreGameUiCallback())") <
                dispatcher.indexOf("runnable.run();"));
    }

    @Test
    public void connectionUiCallbacksUseOwnedDispatcher() throws IOException {
        String source = readSource();

        assertUsesOwnedDispatcher(source,
                "public void stageStarting(final String stage)",
                "public void stageComplete(String stage)");
        assertUsesOwnedDispatcher(source,
                "public void connectionStatusUpdate(final int connectionStatus)",
                "public void connectionStarted()");
        assertUsesOwnedDispatcher(source,
                "public void displayMessage(final String message)",
                "public void displayTransientMessage(final String message)");
        assertUsesOwnedDispatcher(source,
                "public void displayTransientMessage(final String message)",
                "public void rumble(short controllerNumber");
        assertUsesOwnedDispatcher(source,
                "public void onPerfUpdate(final String text)",
                "public void onPerfStatsUpdate(");
        assertUsesOwnedDispatcher(source,
                "public void onPerfStatsUpdate(",
                "public void onVideoStatsUpdate(");
    }

    @Test
    public void stageFailureStopsRetryAndDropsUiAfterDestroy() throws IOException {
        String source = readSource();
        String stageFailed = between(source,
                "public boolean stageFailed(final String stage, final int portFlags, final int errorCode)",
                "private void finishSecondScreen()");

        int destroyedCheck = stageFailed.indexOf("if (inputCallbacksDestroyed)");
        int rejectRetry = stageFailed.indexOf("return false;", destroyedCheck);
        int networkTest = stageFailed.indexOf("MoonBridge.testClientConnectivity");

        assertTrue(destroyedCheck >= 0);
        assertTrue(rejectRetry > destroyedCheck);
        assertTrue(networkTest > rejectRetry);
        assertTrue(stageFailed.contains("runOnUiThreadIfActive(() ->"));
        assertTrue(stageFailed.contains("runOnUiThreadIfActive(new Runnable()"));
    }

    @Test
    public void connectionStartedSeparatesUiAndSynchronousOwnerWork() throws IOException {
        String source = readSource();
        String started = between(source,
                "public void connectionStarted()",
                "public void displayMessage(final String message)");

        assertTrue(started.contains("runOnUiThreadIfActive(new Runnable()"));
        assertTrue(started.contains("runOnUiThreadIfActive(new Runnable()"));
        assertTrue(started.contains("if (shouldIgnoreGameUiCallback())"));

        int ownerLock = started.indexOf("synchronized (gameCallbackOwnerLock)");
        int destroyedCheck = started.indexOf("if (inputCallbacksDestroyed)", ownerLock);
        int keepAliveStart = started.indexOf("StreamKeepAliveService.start(this)", ownerLock);
        int bindUsb = started.indexOf("bindService(new Intent(this, UsbDriverService.class)", ownerLock);
        int shortcutReport = started.indexOf("reportComputerShortcutUsed(computer)", ownerLock);

        assertTrue(ownerLock >= 0);
        assertTrue(destroyedCheck > ownerLock);
        assertTrue(keepAliveStart > destroyedCheck);
        assertTrue(bindUsb > destroyedCheck);
        assertTrue(shortcutReport > destroyedCheck);
    }

    @Test
    public void synchronousDeviceCallbacksShareDestroyOwnerLock() throws IOException {
        String source = readSource();

        assertSynchronousOwnerGuard(source,
                "public void rumble(short controllerNumber",
                "public void rumbleTriggers(short controllerNumber");
        assertSynchronousOwnerGuard(source,
                "public void rumbleTriggers(short controllerNumber",
                "public void setHdrMode(boolean enabled, byte[] hdrMetadata)");
        assertSynchronousOwnerGuard(source,
                "public void setHdrMode(boolean enabled, byte[] hdrMetadata)",
                "public void setMotionEventState(short controllerNumber");
        assertSynchronousOwnerGuard(source,
                "public void setMotionEventState(short controllerNumber",
                "public void setControllerLED(short controllerNumber");
        assertSynchronousOwnerGuard(source,
                "public void setControllerLED(short controllerNumber",
                "public void surfaceChanged(SurfaceHolder holder");
    }

    @Test
    public void destroyClosesCallbackOwnerBeforeTransportTeardown() throws IOException {
        String source = readSource();
        String destroy = between(source,
                "protected void onDestroy()",
                "private boolean isCurrentlyInPip()");

        int ownerLock = destroy.indexOf("synchronized (gameCallbackOwnerLock)");
        int destroyed = destroy.indexOf("inputCallbacksDestroyed = true;", ownerLock);
        int reconnectDestroy = destroy.indexOf("smartReconnectLifecycle.destroy();");
        int superDestroy = destroy.indexOf("super.onDestroy();");

        assertTrue(source.contains("private final Object gameCallbackOwnerLock = new Object();"));
        assertTrue(ownerLock >= 0);
        assertTrue(destroyed > ownerLock);
        assertTrue(reconnectDestroy > destroyed);
        assertTrue(superDestroy > reconnectDestroy);
    }

    private static void assertUsesOwnedDispatcher(String source, String startMarker, String endMarker) {
        String method = between(source, startMarker, endMarker);
        assertTrue(startMarker + " should use active UI dispatcher",
                method.contains("runOnUiThreadIfActive("));
        assertFalse(startMarker + " should not enqueue raw Activity UI work",
                method.contains("runOnUiThread(new Runnable()"));
    }

    private static void assertSynchronousOwnerGuard(String source, String startMarker, String endMarker) {
        String method = between(source, startMarker, endMarker);
        int ownerLock = method.indexOf("synchronized (gameCallbackOwnerLock)");
        int destroyed = method.indexOf("if (inputCallbacksDestroyed)", ownerLock);
        int reject = method.indexOf("return;", destroyed);

        assertTrue(startMarker + " should enter callback owner lock", ownerLock >= 0);
        assertTrue(startMarker + " should check destroyed state inside lock", destroyed > ownerLock);
        assertTrue(startMarker + " should reject destroyed owner", reject > destroyed);
        assertFalse(startMarker + " should remain synchronous rather than queueing across generations",
                method.contains("runOnUiThreadIfActive("));
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
