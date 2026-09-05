package com.limelight.binding.input;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControllerHandlerBatterySuspensionLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/ControllerHandler.java";

    @Test
    public void suspensionStopsQueuedPollingAndResumeDrainsOldWorkerFirst() throws IOException {
        String source = readSource();
        String suspend = between(source, "private void suspendBatteryPolling()",
                "private void resumeBatteryPollingAfterDrain()");
        String resume = between(source, "private void resumeBatteryPollingAfterDrain()",
                "public void suspendForReconnect()");
        String lifecycle = between(source, "public void suspendForReconnect()", "public void stop()");
        int batterySeal = lifecycle.indexOf("suspendBatteryPolling();");
        int reconnectMark = lifecycle.indexOf("suspendedForReconnect = true;");

        assertTrue(source.contains("private final Object batteryPollingLock = new Object();"));
        assertTrue(suspend.contains("synchronized (batteryPollingLock)"));
        assertTrue(suspend.contains("batteryPollingGeneration++;"));
        assertTrue(suspend.contains("batteryPollingSuspended = true;"));
        assertTrue(suspend.contains("removeCallbacks("));
        assertTrue(resume.contains("generation = batteryPollingGeneration;"));
        assertTrue(resume.contains("backgroundThreadHandler.post(() -> mainThreadHandler.post(() ->"));
        assertTrue(resume.contains("generation != batteryPollingGeneration"));
        assertTrue(resume.contains("batteryPollingSuspended = false;"));
        assertTrue(batterySeal >= 0);
        assertTrue(reconnectMark > batterySeal);
        assertTrue(lifecycle.contains("resumeBatteryPollingAfterDrain();"));
    }

    @Test
    public void slowBatteryQuerySerializesFinalOwnershipBeforeNativeSend() throws IOException {
        String source = readSource();
        String method = between(source, "private void sendControllerBatteryPacket(InputDeviceContext context)",
                "private void sendControllerInputPacket");
        int batteryQuery = method.indexOf("context.inputDevice.getBatteryState()");
        int lock = method.indexOf("synchronized (batteryPollingLock)", batteryQuery);
        int finalGuard = method.indexOf("if (!shouldPollBattery(context))", lock);
        int send = method.indexOf("conn.sendControllerBatteryEvent", finalGuard);

        assertTrue(batteryQuery >= 0);
        assertTrue(lock > batteryQuery);
        assertTrue(finalGuard > lock);
        assertTrue(send > finalGuard);
    }

    @Test
    public void contextDestroySharesFinalBatterySendLock() throws IOException {
        String source = readSource();
        int contextStart = source.indexOf("class InputDeviceContext extends GenericControllerContext");
        int destroyStart = source.indexOf("public void destroy() {", contextStart);
        int destroyEnd = source.indexOf("public void sendControllerArrival()", destroyStart);

        assertTrue(contextStart >= 0);
        assertTrue(destroyStart > contextStart);
        assertTrue(destroyEnd > destroyStart);

        String destroy = source.substring(destroyStart, destroyEnd);
        int lock = destroy.indexOf("synchronized (batteryPollingLock)");
        int invalidate = destroy.indexOf("destroyed = true;", lock);
        int parentDestroy = destroy.indexOf("super.destroy();", invalidate);

        assertTrue(lock >= 0);
        assertTrue(invalidate > lock);
        assertTrue(parentDestroy > invalidate);
    }

    @Test
    public void contextMigrationRespectsBatteryReportingPolicy() throws IOException {
        String source = readSource();
        String migration = between(source, "public void migrateContext(InputDeviceContext oldContext)",
                "public void disableSensors()");

        assertTrue(migration.contains("if (shouldPollBattery(this))"));
        assertTrue(migration.contains("backgroundThreadHandler.post(batteryStateUpdateRunnable);"));
    }

    @Test
    public void recurringPollUsesActiveOwnerPolicyOnBothSidesOfQuery() throws IOException {
        String source = readSource();
        String runnable = between(source, "public final Runnable batteryStateUpdateRunnable",
                "public final Runnable enableSensorRunnable");
        int firstGuard = runnable.indexOf("if (!shouldPollBattery(InputDeviceContext.this))");
        int query = runnable.indexOf("sendControllerBatteryPacket(InputDeviceContext.this);");
        int secondGuard = runnable.indexOf("if (shouldPollBattery(InputDeviceContext.this))", query);

        assertTrue(source.contains("private volatile boolean batteryPollingSuspended = false;"));
        assertTrue(firstGuard >= 0);
        assertTrue(query > firstGuard);
        assertTrue(secondGuard > query);
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
