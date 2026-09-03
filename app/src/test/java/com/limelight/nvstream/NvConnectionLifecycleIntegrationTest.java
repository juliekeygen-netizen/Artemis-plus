package com.limelight.nvstream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class NvConnectionLifecycleIntegrationTest {
    @Test
    public void smartReconnectStopsThroughNvConnectionAndClearsStaleConnectedState() throws IOException {
        String game = readSource("src/main/java/com/limelight/Game.java");
        String reconnect = between(game,
                "public void connectionTerminated(final int errorCode)",
                "private void handleConnectionTerminatedFinal");

        assertTrue(reconnect.contains("connected = false;"));
        assertTrue(reconnect.contains("conn.stop();"));
        assertFalse(reconnect.contains("MoonBridge.stopConnection();"));
        assertFalse(reconnect.contains("MoonBridge.cleanupBridge();"));
    }

    @Test
    public void stopTouchesGlobalBridgeOnlyAfterOwningPermitIsTransferred() throws IOException {
        String connection = readSource("src/main/java/com/limelight/nvstream/NvConnection.java");
        String stop = between(connection, "public void stop()", "private InetAddress resolveServerAddress");

        int transfer = stop.indexOf("startGate.invalidateAndReleasePermit()");
        int ownershipGuard = stop.indexOf("if (!ownsNativeConnection)");
        int interrupt = stop.indexOf("MoonBridge.interruptConnection()");
        int release = stop.indexOf("connectionAllowed.release()");

        assertTrue(transfer >= 0);
        assertTrue(ownershipGuard > transfer);
        assertTrue(interrupt > ownershipGuard);
        assertTrue(release > interrupt);
    }

    @Test
    public void startUsesGenerationBoundListenerAndExplicitPermitClaim() throws IOException {
        String connection = readSource("src/main/java/com/limelight/nvstream/NvConnection.java");
        String start = between(connection,
                "public void start(final AudioRenderer",
                "public void sendExecServerCmd");

        assertTrue(start.contains("GenerationBoundNvConnectionListener"));
        assertTrue(start.contains("createStartContext"));
        assertTrue(start.contains("startGate.claimPermit(token)"));
        assertTrue(start.contains("startGate.releasePermit(token)"));
        assertFalse(start.contains("context.connListener = connectionListener"));
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
