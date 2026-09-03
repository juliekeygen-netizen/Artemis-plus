package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameSendKeysLifecycleTest {
    @Test
    public void delayedKeyReleasesStayBoundToOriginalConnection() throws IOException {
        String game = readSource("src/main/java/com/limelight/Game.java");
        String sendKeys = between(game, "public void sendKeys(short[] keys)",
                "public boolean handleFocusChange(boolean hasFocus)");

        assertTrue(sendKeys.contains("NvConnection keyConnection = conn;"));
        assertTrue(sendKeys.contains("short[] keySequence = keys.clone();"));
        assertTrue(sendKeys.contains("keyConnection.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN"));
        assertTrue(sendKeys.contains("keyConnection.sendKeyboardInput(key, KeyboardPacket.KEY_UP"));
        assertTrue(sendKeys.contains("timerHandler.postDelayed"));
        assertFalse(sendKeys.contains("new Handler().postDelayed"));
        assertFalse(sendKeys.contains("conn.sendKeyboardInput"));
    }

    @Test
    public void activityTeardownCancelsPendingKeyReleaseWork() throws IOException {
        String game = readSource("src/main/java/com/limelight/Game.java");
        String onDestroy = between(game, "protected void onDestroy()", "private boolean isCurrentlyInPip()");

        int cancelCallbacks = onDestroy.indexOf("timerHandler.removeCallbacksAndMessages(null);");
        int stopConnection = onDestroy.indexOf("stopConnection");
        assertTrue("Game teardown must cancel Activity-owned delayed work", cancelCallbacks >= 0);
        assertTrue("Pending key releases must be cancelled before transport teardown",
                stopConnection < 0 || cancelCallbacks < stopConnection);
    }

    private static String readSource(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
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
