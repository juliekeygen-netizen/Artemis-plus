from pathlib import Path

GAME_PATH = Path("app/src/main/java/com/limelight/Game.java")
TEST_PATH = Path("app/src/test/java/com/limelight/GameSendKeysLifecycleTest.java")

old = '''    public void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {
            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= (byte) ~getModifier(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), GameMenu.KEY_UP_DELAY);
    }
'''

new = '''    public void sendKeys(short[] keys) {
        NvConnection keyConnection = conn;
        if (keyConnection == null || keys == null || keys.length == 0) {
            return;
        }

        // Pair every delayed key-up with the same connection generation that received its key-down.
        // Reconnect can replace the Game.conn field while this delay is pending.
        short[] keySequence = keys.clone();
        final byte[] modifier = {(byte) 0};

        for (short key : keySequence) {
            keyConnection.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        // Use the Activity-owned handler so onDestroy() can cancel pending releases before transport
        // teardown instead of leaving an anonymous Handler retaining this Game instance.
        timerHandler.postDelayed((() -> {
            for (int pos = keySequence.length - 1; pos >= 0; pos--) {
                short key = keySequence[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= (byte) ~getModifier(key);

                keyConnection.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), GameMenu.KEY_UP_DELAY);
    }
'''

game = GAME_PATH.read_text(encoding="utf-8")
if game.count(old) != 1:
    raise SystemExit(f"Expected exactly one sendKeys block, found {game.count(old)}")
GAME_PATH.write_text(game.replace(old, new, 1), encoding="utf-8")

TEST_PATH.write_text('''package com.limelight;

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
''', encoding="utf-8")
