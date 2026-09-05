package com.limelight.binding.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ControllerHandlerMouseEmulationSuspensionLifecycleTest {
    private static final String SOURCE_PATH =
            "src/main/java/com/limelight/binding/input/ControllerHandler.java";

    @Test
    public void suspensionStopsMouseEmulationBeforeReconnectBoundary() throws IOException {
        String source = readSource();
        String helper = between(source, "private void suspendMouseEmulationForReconnect()",
                "private void resumeMouseEmulationAfterReconnect()");
        String lifecycle = between(source, "public void suspendForReconnect()",
                "public void resumeAfterReconnect()");
        int seal = lifecycle.indexOf("suspendMouseEmulationForReconnect();");
        int reconnectBoundary = lifecycle.indexOf("suspendedForReconnect = true;");

        assertTrue(source.contains("private volatile boolean mouseEmulationCallbacksSuspended = false;"));
        assertTrue(helper.contains("mouseEmulationCallbacksSuspended = true;"));
        assertTrue(helper.contains("defaultContext.suspendMouseEmulation();"));
        assertTrue(helper.contains("inputDeviceContexts.valueAt(i).suspendMouseEmulation();"));
        assertTrue(helper.contains("usbDeviceContexts.valueAt(i).suspendMouseEmulation();"));
        assertTrue(seal >= 0);
        assertTrue(reconnectBoundary > seal);
    }

    @Test
    public void recurringMouseLoopChecksOwnershipAtEntryAndBeforeRequeue() throws IOException {
        String source = readSource();
        String context = between(source, "class GenericControllerContext implements GameInputDevice",
                "class InputDeviceContext extends GenericControllerContext");
        String runnable = between(context, "public final Runnable mouseEmulationRunnable",
                "@Override\n        public List<GameMenu.MenuOption> getGameMenuOptions()");
        int entryGuard = runnable.indexOf(
                "stopped || suspendedForReconnect || mouseEmulationCallbacksSuspended");
        int firstSend = runnable.indexOf("sendEmulatedMouseMove", entryGuard);
        int finalGuard = runnable.indexOf(
                "!stopped && !suspendedForReconnect && !mouseEmulationCallbacksSuspended", firstSend);
        int requeue = runnable.indexOf("mainThreadHandler.postDelayed(this, mouseEmulationReportPeriod)",
                finalGuard);

        assertTrue(entryGuard >= 0);
        assertTrue(firstSend > entryGuard);
        assertTrue(finalGuard > firstSend);
        assertTrue(requeue > finalGuard);
    }

    @Test
    public void suspensionReleasesSyntheticButtonsAndNeutralizesTransientState() throws IOException {
        String source = readSource();
        String context = between(source, "class GenericControllerContext implements GameInputDevice",
                "class InputDeviceContext extends GenericControllerContext");
        String suspend = between(context, "public void suspendMouseEmulation()",
                "public void resumeMouseEmulation()");

        assertTrue(suspend.contains("removeCallbacks(mouseEmulationRunnable)"));
        assertTrue(suspend.contains("conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)"));
        assertTrue(suspend.contains("conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)"));
        assertTrue(suspend.contains("mouseEmulationLastInputMap = 0;"));
        assertTrue(suspend.contains("mouseEmulationXDown = false;"));
        assertTrue(suspend.contains("inputMap = 0;"));
        assertTrue(suspend.contains("leftStickX = 0;"));
        assertTrue(suspend.contains("leftStickY = 0;"));
        assertTrue(suspend.contains("rightStickX = 0;"));
        assertTrue(suspend.contains("rightStickY = 0;"));
        assertFalse(suspend.contains("mouseEmulationPixelMultiplier = 1"));
    }

    @Test
    public void resumeRestartsOnlyActiveOwnedMouseEmulation() throws IOException {
        String source = readSource();
        String helper = between(source, "private void resumeMouseEmulationAfterReconnect()",
                "public void suspendForReconnect()");
        String context = between(source, "class GenericControllerContext implements GameInputDevice",
                "class InputDeviceContext extends GenericControllerContext");
        String resume = between(context, "public void resumeMouseEmulation()",
                "public void destroy()");
        String lifecycle = between(source, "public void resumeAfterReconnect()", "public void stop()");

        assertTrue(helper.contains("if (stopped || suspendedForReconnect)"));
        assertTrue(helper.contains("mouseEmulationCallbacksSuspended = false;"));
        assertTrue(helper.contains("defaultContext.resumeMouseEmulation();"));
        assertTrue(resume.contains("mouseEmulationActive && !stopped && !suspendedForReconnect"));
        assertTrue(resume.contains("!mouseEmulationCallbacksSuspended"));
        assertTrue(lifecycle.contains("resumeMouseEmulationAfterReconnect();"));
    }

    @Test
    public void toggleCannotScheduleLoopWhileSuspended() throws IOException {
        String source = readSource();
        String context = between(source, "class GenericControllerContext implements GameInputDevice",
                "class InputDeviceContext extends GenericControllerContext");
        String toggle = between(context, "public void toggleMouseEmulation()",
                "public void suspendMouseEmulation()");

        assertTrue(toggle.contains("mouseEmulationActive && !stopped && !suspendedForReconnect"));
        assertTrue(toggle.contains("!mouseEmulationCallbacksSuspended"));
        assertTrue(toggle.contains("mainThreadHandler.postDelayed(mouseEmulationRunnable, mouseEmulationReportPeriod)"));
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
