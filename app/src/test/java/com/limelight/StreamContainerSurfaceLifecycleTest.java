package com.limelight;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class StreamContainerSurfaceLifecycleTest {
    @Test
    public void teardownDetachesSurfaceProducersBeforeReleasingRenderState() throws IOException {
        String container = readSource("src/main/java/com/limelight/ui/StreamContainer.java");
        String onDestroy = between(container, "public void onDestroy()", "\n    }\n}");

        int destroyedGuard = onDestroy.indexOf("if (destroyed)");
        int markDestroyed = onDestroy.indexOf("destroyed = true;");
        int clearReadyCallback = onDestroy.indexOf("onSurfaceAvailable = null;");
        int removeHolderCallback = onDestroy.indexOf("mSurfaceView.getHolder().removeCallback(this);");
        int removeTextureListener = onDestroy.indexOf("mTextureView.setSurfaceTextureListener(null);");
        int rendererTeardown = onDestroy.indexOf("mStereoRenderer.onSurfaceDestroyed();");
        int clearSurface = onDestroy.indexOf("mCurrentSurface = null;");

        assertTrue(destroyedGuard >= 0);
        assertTrue(markDestroyed > destroyedGuard);
        assertTrue(clearReadyCallback > markDestroyed);
        assertTrue(removeHolderCallback > clearReadyCallback);
        assertTrue(removeTextureListener > removeHolderCallback);
        assertTrue(rendererTeardown > removeTextureListener);
        assertTrue(clearSurface > rendererTeardown);
        assertTrue(container.contains("private volatile boolean destroyed = false;"));
    }

    @Test
    public void lateSurfaceCallbacksAreRejectedAfterTeardown() throws IOException {
        String container = readSource("src/main/java/com/limelight/ui/StreamContainer.java");

        assertStartsWithDestroyedGuard(container, "public void surfaceCreated(SurfaceHolder holder)");
        assertStartsWithDestroyedGuard(container,
                "public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)");
        assertStartsWithDestroyedGuard(container, "public void surfaceDestroyed(SurfaceHolder holder)");

        String stereoReady = between(container,
                "public void onStereo3DSurfaceReady(Surface surface)", "private void closeTextureSurface()");
        assertTrue(stereoReady.contains("if (destroyed || renderMode == StreamMode.MODE_2D)"));
        assertTrue(stereoReady.contains("if (destroyed)"));

        String notifyReady = between(container, "private void notifySurfaceReady()", "@Override\n    public void surfaceCreated");
        assertTrue(notifyReady.contains("if (destroyed)"));
        assertTrue(notifyReady.contains("if (!destroyed && callback != null)"));

        String setReadyCallback = between(container,
                "public void setOnSurfaceAvailable(Runnable callback)", "public Surface getSurface()");
        assertTrue(setReadyCallback.contains("if (destroyed)"));
    }

    @Test
    public void lateTextureCallbacksCannotReattachDestroyedGame() throws IOException {
        String container = readSource("src/main/java/com/limelight/ui/StreamContainer.java");
        String textureListener = between(container,
                "mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener()",
                "addView(mTextureView, childParams);");

        assertTrue(textureListener.contains("onSurfaceTextureAvailable"));
        assertTrue(textureListener.contains("onSurfaceTextureSizeChanged"));
        assertTrue(textureListener.contains("onSurfaceTextureDestroyed"));
        assertTrue(countOccurrences(textureListener, "if (destroyed)") >= 3);

        String attach = between(container, "private void attachSidewaysTexture", "// --- Aspect Ratio and Scaling Logic ---");
        assertTrue(attach.contains("if (destroyed || surfaceTexture == null)"));
    }

    private static void assertStartsWithDestroyedGuard(String source, String methodMarker) {
        int start = source.indexOf(methodMarker);
        assertTrue("Missing method: " + methodMarker, start >= 0);
        String methodStart = source.substring(start, Math.min(source.length(), start + 220));
        assertTrue("Missing destroyed guard in: " + methodMarker,
                methodStart.contains("if (destroyed)"));
    }

    private static int countOccurrences(String source, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
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
