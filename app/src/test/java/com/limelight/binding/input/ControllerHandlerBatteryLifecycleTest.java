package com.limelight.binding.input;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControllerHandlerBatteryLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/ControllerHandler.java";

    @Test
    public void batteryPollRechecksOwnershipBeforeRequeue() throws IOException {
        String source = readSource();
        String helper = between(source,
                "private boolean shouldPollBattery(InputDeviceContext context)",
                "private void suspendBatteryPolling()");
        String runnable = between(source,
                "public final Runnable batteryStateUpdateRunnable",
                "public final Runnable enableSensorRunnable");

        assertTrue(source.contains("private volatile boolean destroyed;"));
        assertTrue(helper.contains("!stopped"));
        assertTrue(helper.contains("!batteryPollingSuspended"));
        assertTrue(helper.contains("!context.destroyed"));

        int initialGuard = runnable.indexOf(
                "if (!shouldPollBattery(InputDeviceContext.this))");
        int sendPacket = runnable.indexOf(
                "sendControllerBatteryPacket(InputDeviceContext.this);",
                initialGuard);
        int requeueGuard = runnable.indexOf(
                "if (shouldPollBattery(InputDeviceContext.this))",
                sendPacket);
        int requeue = runnable.indexOf(
                "backgroundThreadHandler.postDelayed(this, BATTERY_RECHECK_INTERVAL_MS);",
                requeueGuard);

        assertTrue(initialGuard >= 0);
        assertTrue(sendPacket > initialGuard);
        assertTrue(requeueGuard > sendPacket);
        assertTrue(requeue > requeueGuard);
    }

    @Test
    public void destroyInvalidatesPollingBeforeTeardownAndCancellation() throws IOException {
        String source = readSource();
        int runnableStart = source.indexOf("public final Runnable batteryStateUpdateRunnable");
        int destroyStart = source.indexOf("public void destroy() {", runnableStart);
        int destroyEnd = source.indexOf("public void sendControllerArrival()", destroyStart);

        assertTrue(runnableStart >= 0);
        assertTrue(destroyStart > runnableStart);
        assertTrue(destroyEnd > destroyStart);

        String destroy = source.substring(destroyStart, destroyEnd);
        int invalidate = destroy.indexOf("destroyed = true;");
        int parentDestroy = destroy.indexOf("super.destroy();");
        int removeBattery = destroy.indexOf(
                "backgroundThreadHandler.removeCallbacks(batteryStateUpdateRunnable);");

        assertTrue(invalidate >= 0);
        assertTrue(parentDestroy > invalidate);
        assertTrue(removeBattery > parentDestroy);
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
