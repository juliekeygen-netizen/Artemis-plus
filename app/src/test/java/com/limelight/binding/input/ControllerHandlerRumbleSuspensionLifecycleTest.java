package com.limelight.binding.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControllerHandlerRumbleSuspensionLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/ControllerHandler.java";

    @Test
    public void suspensionSealsAndCancelsEveryRumbleOwnerBeforeReconnectBoundary() throws IOException {
        String source = readSource();
        String helper = between(source, "private void suspendRumbleOutputs()",
                "private void resumeRumbleOutputsAfterReconnect()");
        String lifecycle = between(source, "public void suspendForReconnect()",
                "public void resumeAfterReconnect()");
        int seal = lifecycle.indexOf("suspendRumbleOutputs();");
        int reconnectBoundary = lifecycle.indexOf("suspendedForReconnect = true;");

        assertTrue(source.contains("private final Object rumbleLifecycleLock = new Object();"));
        assertTrue(source.contains("private boolean rumbleSuspended = false;"));
        assertTrue(helper.contains("synchronized (rumbleLifecycleLock)"));
        assertTrue(helper.contains("rumbleSuspended = true;"));
        assertTrue(helper.contains("deviceVibratorManager.cancel();"));
        assertTrue(helper.contains("deviceVibrator.cancel();"));
        assertTrue(helper.contains("context.vibratorManager.cancel();"));
        assertTrue(helper.contains("context.vibrator.cancel();"));
        assertTrue(helper.contains("sceManager.rumble(context.inputDevice, (short) 0, (short) 0);"));
        assertTrue(helper.contains("context.device.rumble((short) 0, (short) 0);"));
        assertTrue(helper.contains("context.device.rumbleTriggers((short) 0, (short) 0);"));
        assertTrue(seal >= 0);
        assertTrue(reconnectBoundary > seal);
    }

    @Test
    public void basicRumbleCallbackHoldsLifecycleLockThroughHardwareDispatch() throws IOException {
        String source = readSource();
        String wrapper = between(source, "public void handleRumble(short controllerNumber",
                "private void handleRumbleLocked(short controllerNumber");
        String locked = between(source, "private void handleRumbleLocked(short controllerNumber",
                "public void handleRumbleTriggers(short controllerNumber");

        assertTrue(wrapper.contains("synchronized (rumbleLifecycleLock)"));
        assertTrue(wrapper.contains("handleRumbleLocked(controllerNumber, lowFreqMotor, highFreqMotor);"));
        assertTrue(locked.contains("if (stopped || rumbleSuspended)"));
        assertTrue(locked.contains("deviceContext.device.rumble(lowFreqMotor, highFreqMotor);"));
    }

    @Test
    public void triggerRumbleCallbackHoldsLifecycleLockThroughHardwareDispatch() throws IOException {
        String source = readSource();
        String wrapper = between(source, "public void handleRumbleTriggers(short controllerNumber",
                "private void handleRumbleTriggersLocked(short controllerNumber");
        String locked = between(source, "private void handleRumbleTriggersLocked(short controllerNumber",
                "private SensorEventListener createSensorListener");

        assertTrue(wrapper.contains("synchronized (rumbleLifecycleLock)"));
        assertTrue(wrapper.contains("handleRumbleTriggersLocked(controllerNumber, leftTrigger, rightTrigger);"));
        assertTrue(locked.contains("if (stopped || rumbleSuspended)"));
        assertTrue(locked.contains("deviceContext.device.rumbleTriggers(leftTrigger, rightTrigger);"));
    }

    @Test
    public void suspensionClearsCachedMotorStateAndResumeDoesNotReplayRumble() throws IOException {
        String source = readSource();
        String suspend = between(source, "private void suspendRumbleOutputs()",
                "private void resumeRumbleOutputsAfterReconnect()");
        String resume = between(source, "private void resumeRumbleOutputsAfterReconnect()",
                "public void suspendForReconnect()");

        assertTrue(suspend.contains("context.lowFreqMotor = 0;"));
        assertTrue(suspend.contains("context.highFreqMotor = 0;"));
        assertTrue(suspend.contains("context.leftTriggerMotor = 0;"));
        assertTrue(suspend.contains("context.rightTriggerMotor = 0;"));
        assertTrue(resume.contains("if (stopped || suspendedForReconnect)"));
        assertTrue(resume.contains("rumbleSuspended = false;"));
        assertFalse(resume.contains("handleRumbleLocked"));
        assertFalse(resume.contains("device.rumble("));
    }

    @Test
    public void finalStopCancelsRumbleInsteadOfOnlyReconnectSuspension() throws IOException {
        String source = readSource();
        String stop = between(source, "public void stop()", "public void destroy()");
        int stopped = stop.indexOf("stopped = true;");
        int cancel = stop.indexOf("suspendRumbleOutputs();", stopped);

        assertTrue(stopped >= 0);
        assertTrue(cancel > stopped);
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
