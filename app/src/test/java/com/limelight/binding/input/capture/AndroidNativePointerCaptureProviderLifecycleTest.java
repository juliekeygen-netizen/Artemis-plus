package com.limelight.binding.input.capture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class AndroidNativePointerCaptureProviderLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProvider.java";

    @Test
    public void delayedRecaptureIsOwnedAndRevalidatesLifecycleState() throws IOException {
        String source = readSource();
        String constructor = between(source,
                "public AndroidNativePointerCaptureProvider(Activity activity, View targetView)",
                "public static boolean isCaptureProviderSupported()");
        String focus = between(source,
                "public void onWindowFocusChanged(boolean focusActive)",
                "public boolean eventHasRelativeMouseAxes(MotionEvent event)");

        assertTrue(constructor.contains("new Handler(activity.getMainLooper())"));
        assertFalse(constructor.contains("new Handler()"));
        assertTrue(constructor.contains("destroyed || !isCapturing || isCursorVisible"));
        assertTrue(constructor.contains("!targetView.isAttachedToWindow() || !targetView.hasWindowFocus()"));

        int reject = focus.indexOf("destroyed || !focusActive || !isCapturing || isCursorVisible");
        int cancelRejected = focus.indexOf("recaptureHandler.removeCallbacks(recaptureRunnable);", reject);
        int cancelOld = focus.indexOf("recaptureHandler.removeCallbacks(recaptureRunnable);", cancelRejected + 1);
        int schedule = focus.indexOf("recaptureHandler.postDelayed(recaptureRunnable, 500);");

        assertTrue(reject >= 0);
        assertTrue(cancelRejected > reject);
        assertTrue(cancelOld > cancelRejected);
        assertTrue(schedule > cancelOld);
    }

    @Test
    public void destroyCancelsWorkAndReleasesOwnedCaptureState() throws IOException {
        String source = readSource();
        String destroy = between(source,
                "public void destroy()",
                "public void onWindowFocusChanged(boolean focusActive)");
        String showCursor = between(source,
                "public void showCursor()",
                "public void hideCursor()");
        String hideCursor = between(source,
                "public void hideCursor()",
                "public void destroy()");

        int destroyed = destroy.indexOf("destroyed = true;");
        int stopCapture = destroy.indexOf("isCapturing = false;");
        int cancel = destroy.indexOf("recaptureHandler.removeCallbacks(recaptureRunnable);");
        int unregister = destroy.indexOf("unregisterInputDeviceListener();");
        int release = destroy.indexOf("targetView.releasePointerCapture();");
        int restoreCursor = destroy.indexOf("super.showCursor();");

        assertTrue(destroyed >= 0);
        assertTrue(stopCapture > destroyed);
        assertTrue(cancel > stopCapture);
        assertTrue(unregister > cancel);
        assertTrue(release > unregister);
        assertTrue(restoreCursor > release);

        assertTrue(source.contains("private boolean inputDeviceListenerRegistered;"));
        assertTrue(source.contains("if (!inputDeviceListenerRegistered)"));
        assertTrue(source.contains("if (inputDeviceListenerRegistered)"));
        assertTrue(showCursor.contains("recaptureHandler.removeCallbacks(recaptureRunnable);"));
        assertTrue(showCursor.indexOf("unregisterInputDeviceListener();") <
                showCursor.indexOf("targetView.releasePointerCapture();"));
        assertTrue(hideCursor.contains("registerInputDeviceListener();"));
    }

    @Test
    public void lateInputDeviceCallbacksCannotRecaptureAfterDisableOrDestroy() throws IOException {
        String source = readSource();
        String added = between(source,
                "public void onInputDeviceAdded(int deviceId)",
                "public void onInputDeviceRemoved(int deviceId)");
        String removed = between(source,
                "public void onInputDeviceRemoved(int deviceId)",
                "public void onInputDeviceChanged(int deviceId)");
        String changed = source.substring(source.indexOf("public void onInputDeviceChanged(int deviceId)"));
        String guard = "destroyed || !isCapturing || isCursorVisible";

        assertTrue(added.contains(guard));
        assertTrue(removed.contains(guard));
        assertTrue(changed.contains(guard));
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
