package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameStopWorkerLifecycleTest {
    private static final String SOURCE_PATH = "src/main/java/com/limelight/Game.java";

    @Test
    public void normalStopCompletionAndQuitToastsAreActivityOwned() throws IOException {
        String source = readSource();
        String stop = between(source,
                "private void stopConnection(boolean preserveControllerStateForReconnect, Runnable onStopped,",
                "@Override\n    public boolean stageFailed");

        assertTrue(stop.contains("runOnUiThreadIfActive(() -> Toast.makeText"));
        assertTrue(stop.contains("runOnUiThreadIfActive(completion);"));
        assertTrue(stop.contains("Thread.currentThread().interrupt();"));
        assertFalse(stop.contains("Game.this.runOnUiThread(() -> Toast.makeText"));
    }

    @Test
    public void destroyUsesOnlyExplicitTeardownSafeCompletionPath() throws IOException {
        String source = readSource();
        String destroy = between(source,
                "protected void onDestroy()",
                "private boolean isCurrentlyInPip()");
        String overloads = between(source,
                "private void stopConnection()",
                "@Override\n    public boolean stageFailed");

        assertTrue(destroy.contains("stopConnectionForDestroy(() ->"));
        assertFalse(destroy.contains("stopConnection(false, () ->"));
        assertTrue(overloads.contains("private void stopConnectionForDestroy(Runnable onStopped)"));
        assertTrue(overloads.contains("stopConnection(false, onStopped, true);"));
        assertTrue(overloads.contains("if (allowCompletionAfterDestroy)"));
        assertTrue(overloads.contains("runOnUiThread(completion);"));
    }

    @Test
    public void stopWorkerSnapshotsTransportBeforeAsyncTeardown() throws IOException {
        String source = readSource();
        String stop = between(source,
                "private void stopConnection(boolean preserveControllerStateForReconnect, Runnable onStopped,",
                "@Override\n    public boolean stageFailed");

        int snapshot = stop.indexOf("NvConnection connectionToStop = conn;");
        int worker = stop.indexOf("new Thread(() ->", snapshot);
        int stopCall = stop.indexOf("connectionToStop.stop();", worker);
        assertTrue(snapshot >= 0);
        assertTrue(worker > snapshot);
        assertTrue(stopCall > worker);
        assertFalse(stop.substring(worker).contains("conn.stop();"));
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
