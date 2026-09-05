package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameDelayedCallbackLifecycleTest {
    private static final String SOURCE_PATH = "src/main/java/com/limelight/Game.java";
    private static final String LIFECYCLE_GUARD =
            "inputCallbacksDestroyed || isFinishing() || isDestroyed()";

    @Test
    public void immersiveUiDelayIsOwnedByActivityHandler() throws IOException {
        String source = readSource();
        String runnable = between(source,
                "private final Runnable hideSystemUi = new Runnable()",
                "private void hideSystemUi(int delay)");
        String scheduler = between(source,
                "private void hideSystemUi(int delay)",
                "@Override\n    @TargetApi(Build.VERSION_CODES.N)");

        assertTrue(runnable.contains(LIFECYCLE_GUARD));
        assertTrue(scheduler.contains("timerHandler.removeCallbacks(hideSystemUi);"));
        assertTrue(scheduler.contains("timerHandler.postDelayed(hideSystemUi, delay);"));
        assertTrue(scheduler.contains("!inputCallbacksDestroyed"));
        assertFalse(scheduler.contains("getDecorView().getHandler()"));
    }

    @Test
    public void delayedInputGrabCannotOutliveGame() throws IOException {
        String source = readSource();
        String runnable = between(source,
                "private final Runnable toggleGrab = new Runnable()",
                "private void setMetaKeyCaptureState");
        String keyBlock = between(source,
                "case KeyEvent.KEYCODE_Z:",
                "case KeyEvent.KEYCODE_Q:");

        assertTrue(runnable.contains(LIFECYCLE_GUARD));
        assertTrue(keyBlock.contains("timerHandler.removeCallbacks(toggleGrab);"));
        assertTrue(keyBlock.contains("timerHandler.postDelayed(toggleGrab, 250);"));
        assertTrue(keyBlock.contains("!inputCallbacksDestroyed"));
        assertFalse(keyBlock.contains("getDecorView().getHandler()"));
    }

    @Test
    public void secondScreenFinishUsesOwnedCancelableRunnable() throws IOException {
        String source = readSource();
        String runnable = between(source,
                "private final Runnable finishSecondScreenRunnable",
                "private final Runnable flushCommitTextQueue");
        String scheduler = between(source,
                "private void finishSecondScreen()",
                "private void handleConnectionTerminatedFinal");

        assertTrue(runnable.contains(LIFECYCLE_GUARD));
        assertTrue(runnable.contains("finish();"));
        assertTrue(scheduler.contains("timerHandler.removeCallbacks(finishSecondScreenRunnable);"));
        assertTrue(scheduler.contains("timerHandler.postDelayed(finishSecondScreenRunnable, 2000);"));
        assertTrue(scheduler.contains("!inputCallbacksDestroyed"));
        assertFalse(scheduler.contains("new Handler()"));
    }

    @Test
    public void activityDestroyCancelsOwnedDelayedCallbacks() throws IOException {
        String source = readSource();
        String onDestroy = between(source,
                "protected void onDestroy()",
                "private boolean isCurrentlyInPip()");

        int destroyed = onDestroy.indexOf("inputCallbacksDestroyed = true;");
        int cancelTimer = onDestroy.indexOf("timerHandler.removeCallbacksAndMessages(null);");

        assertTrue(destroyed >= 0);
        assertTrue(cancelTimer > destroyed);
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
