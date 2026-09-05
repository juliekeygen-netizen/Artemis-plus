package com.limelight.binding.input.driver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class UsbDriverServiceLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/driver/UsbDriverService.java";

    @Test
    public void delayedUsbAttachWorkIsOwnedByServiceHandler() throws IOException {
        String source = readSource();
        String receiver = between(source,
                "public class UsbEventReceiver extends BroadcastReceiver",
                "public class UsbDriverBinder extends Binder");

        assertTrue(source.contains("private final Handler mainHandler = new Handler(Looper.getMainLooper());"));
        assertTrue(receiver.contains("if (!started) {\n                return;"));
        assertTrue(receiver.contains("mainHandler.postDelayed(new Runnable()"));
        assertTrue(receiver.contains("public void run() {\n                        if (!started) {"));
        assertFalse(receiver.contains("new Handler().postDelayed"));
    }

    @Test
    public void deviceStateProcessingRejectsStoppedOrMissingDevices() throws IOException {
        String source = readSource();
        String handler = between(source,
                "private void handleUsbDeviceState(UsbDevice device)",
                "public static boolean isRecognizedInputDevice");

        assertTrue(handler.contains("if (!started || device == null) {"));
        int guard = handler.indexOf("if (!started || device == null) {");
        int claim = handler.indexOf("shouldClaimDevice(device, prefConfig.bindAllUsb)");
        assertTrue(guard >= 0);
        assertTrue(claim > guard);
    }

    @Test
    public void stopCancelsPendingUsbWorkEvenWhenAlreadyStopped() throws IOException {
        String source = readSource();
        String stop = between(source,
                "private void stop()",
                "@Override\n    public void onCreate()");

        int cancel = stop.indexOf("mainHandler.removeCallbacksAndMessages(null);");
        int earlyReturn = stop.indexOf("if (!started) {");
        assertTrue(cancel >= 0);
        assertTrue(earlyReturn > cancel);
        assertTrue(stop.contains("started = false;"));
    }

    private static String readSource() throws IOException {
        Path path = Paths.get(SOURCE_PATH);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("Missing start marker: " + startMarker, start >= 0);
        assertTrue("Missing end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }
}
