package com.limelight;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameCommitTextLifecycleTest {
    @Test
    public void gameTeardownRejectsAndDiscardsCommitTextWork() throws IOException {
        String game = readSource("src/main/java/com/limelight/Game.java");
        String onDestroy = between(game, "protected void onDestroy()", "private boolean isCurrentlyInPip()");
        String flush = between(game, "private final Runnable flushCommitTextQueue", "private final Runnable backgroundPing");
        String callbacks = between(game, "public boolean handleCommitText", "private SurfaceView findFirstSurfaceViewFrom");

        int destroyed = onDestroy.indexOf("inputCallbacksDestroyed = true;");
        int removeCallbacks = onDestroy.indexOf("commitTextHandler.removeCallbacksAndMessages(null);");
        int clearQueue = onDestroy.indexOf("commitTextQueue.clear();");
        int containerDestroy = onDestroy.indexOf("streamContainer.onDestroy();");

        assertTrue(destroyed >= 0);
        assertTrue(removeCallbacks > destroyed);
        assertTrue(clearQueue > removeCallbacks);
        assertTrue(containerDestroy > clearQueue);
        assertTrue(flush.contains("if (inputCallbacksDestroyed)"));
        assertTrue(flush.contains("commitTextQueue.clear();"));
        assertTrue(callbacks.contains("inputCallbacksDestroyed || !prefConfig.enableCommitText || conn == null"));
        assertTrue(callbacks.contains("if (inputCallbacksDestroyed || text == null || text.isEmpty())"));
    }

    @Test
    public void streamContainerDropsLateInputConnectionCallbackTarget() throws IOException {
        String container = readSource("src/main/java/com/limelight/ui/StreamContainer.java");
        int onDestroyStart = container.indexOf("public void onDestroy()");
        assertTrue("Missing StreamContainer.onDestroy()", onDestroyStart >= 0);
        String onDestroy = container.substring(onDestroyStart);
        String inputConnection = between(container, "public InputConnection onCreateInputConnection", "public void setOnSurfaceAvailable");

        assertTrue(onDestroy.contains("mInputCallbacks = null;"));
        assertTrue(onDestroy.contains("commitTextEnabled = false;"));
        assertTrue(inputConnection.contains("mInputCallbacks.handleCommitText(text)"));
        assertTrue(inputConnection.contains("mInputCallbacks.handleDeleteSurroundingText"));
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
