package com.limelight.binding.input;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControllerHandlerSensorSuspensionLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/ControllerHandler.java";

    @Test
    public void suspensionSealsSensorOwnershipBeforeReconnectBoundary() throws IOException {
        String source = readSource();
        String helper = between(source, "private void suspendSensorsForReconnect()",
                "private void resumeSensorsAfterReconnect()");
        String lifecycle = between(source, "public void suspendForReconnect()",
                "public void resumeAfterReconnect()");
        int seal = lifecycle.indexOf("suspendSensorsForReconnect();");
        int reconnectBoundary = lifecycle.indexOf("suspendedForReconnect = true;");

        assertTrue(source.contains("private final Object sensorLifecycleLock = new Object();"));
        assertTrue(source.contains("private boolean sensorRegistrationSuspended = false;"));
        assertTrue(helper.contains("synchronized (sensorLifecycleLock)"));
        assertTrue(helper.contains("sensorRegistrationSuspended = true;"));
        assertTrue(helper.indexOf("sensorRegistrationSuspended = true;") < helper.indexOf("disableSensors();"));
        assertTrue(seal >= 0);
        assertTrue(reconnectBoundary > seal);
    }

    @Test
    public void suspendedHostRateUpdateIsRememberedButRegistrationIsDeferred() throws IOException {
        String source = readSource();
        String method = between(source, "public void handleSetMotionEventState",
                "public void handleSetControllerLED");
        int ownerLock = method.indexOf("synchronized (sensorLifecycleLock)");
        int desiredRate = method.indexOf("deviceContext.accelReportRateHz = reportRateHz;");
        int deferred = method.indexOf("if (sensorRegistrationSuspended || deviceContext.destroyed)");
        int register = method.indexOf("sm.registerListener", deferred);

        assertTrue(ownerLock >= 0);
        assertTrue(desiredRate > ownerLock);
        assertTrue(deferred > desiredRate);
        assertTrue(register > deferred);
        assertTrue(method.contains("createSensorListener(deviceContext, motionType, sm == deviceSensorManager)"));
    }

    @Test
    public void delayedEnableAndContextDestroyShareSensorOwnershipLock() throws IOException {
        String source = readSource();
        String context = between(source, "class InputDeviceContext extends GenericControllerContext",
                "class UsbDeviceContext extends InputDeviceContext");
        String runnable = between(context, "public final Runnable enableSensorRunnable",
                "public void destroy()");
        String destroy = between(context, "public void destroy()", "public void sendControllerArrival()");

        assertTrue(runnable.contains("synchronized (sensorLifecycleLock)"));
        assertTrue(runnable.contains("stopped || sensorRegistrationSuspended || destroyed"));
        assertTrue(destroy.contains("synchronized (sensorLifecycleLock)"));
        assertTrue(destroy.contains("removeCallbacks(enableSensorRunnable)"));
        assertTrue(destroy.contains("sensorManager.unregisterListener(gyroListener)"));
        assertTrue(destroy.contains("sensorManager.unregisterListener(accelListener)"));
    }

    @Test
    public void sensorCallbackSerializesFinalSendAgainstSuspensionAndDestroy() throws IOException {
        String source = readSource();
        String listener = between(source, "private SensorEventListener createSensorListener",
                "public void handleSetMotionEventState");
        int lock = listener.indexOf("synchronized (sensorLifecycleLock)");
        int guard = listener.indexOf("stopped || sensorRegistrationSuspended || context.destroyed", lock);
        int send = listener.indexOf("conn.sendControllerMotionEvent", guard);

        assertTrue(listener.contains("createSensorListener(final InputDeviceContext context"));
        assertTrue(lock >= 0);
        assertTrue(guard > lock);
        assertTrue(send > guard);
        assertTrue(listener.contains("(byte) context.controllerNumber"));
    }

    @Test
    public void resumeReopensSensorRegistrationOnlyForActiveOwner() throws IOException {
        String source = readSource();
        String helper = between(source, "private void resumeSensorsAfterReconnect()",
                "public void suspendForReconnect()");
        String resume = between(source, "public void resumeAfterReconnect()", "public void stop()");
        String topLevelEnable = between(source, "public void enableSensors()",
                "private static boolean hasJoystickAxes");

        assertTrue(helper.contains("synchronized (sensorLifecycleLock)"));
        assertTrue(helper.contains("if (stopped || suspendedForReconnect)"));
        assertTrue(helper.indexOf("sensorRegistrationSuspended = false;") < helper.indexOf("enableSensors();"));
        assertTrue(resume.contains("resumeSensorsAfterReconnect();"));
        assertTrue(topLevelEnable.contains("stopped || sensorRegistrationSuspended"));
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
