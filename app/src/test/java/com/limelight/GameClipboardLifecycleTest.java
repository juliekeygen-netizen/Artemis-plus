package com.limelight;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GameClipboardLifecycleTest {
    private static final String SOURCE_PATH = "src/main/java/com/limelight/Game.java";

    @Test
    public void destroyInvalidatesOrdinaryClipboardWorkBeforeTransportTeardown() throws IOException {
        String source = readSource();
        String destroy = between(source,
                "protected void onDestroy()",
                "private boolean isCurrentlyInPip()");

        int destroyedFlag = destroy.indexOf("inputCallbacksDestroyed = true;");
        int clipboardDestroy = destroy.indexOf("clipboardSyncLifecycle.destroy();");
        int reconnectDestroy = destroy.indexOf("smartReconnectLifecycle.destroy();");
        int superDestroy = destroy.indexOf("super.onDestroy();");

        assertTrue(source.contains("private final ClipboardSyncLifecycle clipboardSyncLifecycle = new ClipboardSyncLifecycle();"));
        assertFalse(source.contains("private boolean clipboardSyncRunning"));
        assertTrue(destroyedFlag >= 0);
        assertTrue(clipboardDestroy > destroyedFlag);
        assertTrue(reconnectDestroy > clipboardDestroy);
        assertTrue(superDestroy > reconnectDestroy);
    }

    @Test
    public void clipboardManagerIsApplicationScopedForTeardownSafeFinalFetch() throws IOException {
        String source = readSource();
        assertTrue(source.contains(
                "clipboardManager = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);"));
    }

    @Test
    public void sendWorkerUsesGenerationTokenAndWeakActivityUi() throws IOException {
        String source = readSource();
        String send = between(source,
                "public boolean sendClipboard(boolean force)",
                "private boolean startClipboardGet(int delay, boolean finalDisconnectFetch)");

        int begin = send.indexOf("long token = lifecycle.beginSend();");
        int activeBeforeNetwork = send.indexOf("if (!lifecycle.isActive(token))", begin);
        int network = send.indexOf("clipboardHttp.sendClipboard(clipboardText)", activeBeforeNetwork);
        int activeAfterNetwork = send.indexOf("if (!lifecycle.isActive(token) || !showToast)", network);

        assertTrue(begin >= 0);
        assertTrue(activeBeforeNetwork > begin);
        assertTrue(network > activeBeforeNetwork);
        assertTrue(activeAfterNetwork > network);
        assertTrue(send.contains("WeakReference<Game> activityRef = new WeakReference<>(this);"));
        assertTrue(send.contains("showClipboardToast(activityRef"));
        assertFalse(send.contains("Game.this.runOnUiThread("));
        assertFalse(send.contains("httpConn.sendClipboard("));
    }

    @Test
    public void getWorkerCoalescesOwnershipAndRechecksAroundSideEffects() throws IOException {
        String source = readSource();
        String get = between(source,
                "private boolean startClipboardGet(int delay, boolean finalDisconnectFetch)",
                "public boolean getClipboard(int delay)");

        int token = get.indexOf("finalDisconnectFetch ? lifecycle.beginFinalGet() : lifecycle.beginGet()");
        int firstActive = get.indexOf("if (!lifecycle.isActive(token))", token);
        int network = get.indexOf("clipboardHttp.getClipboard();", firstActive);
        int afterNetwork = get.indexOf("if (!lifecycle.isActive(token))", network);
        int atomicMutation = get.indexOf("lifecycle.runIfActive(token", afterNetwork);
        int mutation = get.indexOf("activeClipboardManager.setPrimaryClip(clipData);", atomicMutation);
        int finallyBlock = get.indexOf("finally", mutation);
        int finish = get.indexOf("lifecycle.finishGet(token);", finallyBlock);

        assertTrue(token >= 0);
        assertTrue(firstActive > token);
        assertTrue(network > firstActive);
        assertTrue(afterNetwork > network);
        assertTrue(atomicMutation > afterNetwork);
        assertTrue(mutation > atomicMutation);
        assertTrue(finallyBlock > mutation);
        assertTrue(finish > finallyBlock);
        assertTrue(get.contains("ClipboardManager activeClipboardManager = clipboardManager;"));
        assertTrue(get.contains("WeakReference<Game> activityRef = new WeakReference<>(this);"));
        assertFalse(get.contains("clipboardSyncRunning"));
        assertFalse(get.contains("Game.this.runOnUiThread("));
        assertFalse(get.contains("httpConn.getClipboard()"));
    }

    @Test
    public void disconnectUsesFinalClipboardOwnerBeforeFinishingActivity() throws IOException {
        String source = readSource();
        String publicGet = between(source,
                "public boolean getClipboard(int delay)",
                "private TouchContext getTouchContext(");
        String disconnect = between(source,
                "public void disconnect()",
                "public void quit()");

        assertTrue(publicGet.contains("return startClipboardGet(Math.max(0, delay), false);"));
        assertTrue(publicGet.contains("return startClipboardGet(0, true);"));

        int finalGet = disconnect.indexOf("getClipboardForDisconnect();");
        int finish = disconnect.indexOf("finish();");
        assertTrue(finalGet >= 0);
        assertTrue(finish > finalGet);
        assertFalse(disconnect.contains("getClipboard(-1)"));
    }

    @Test
    public void clipboardToastUsesActiveUiDispatcher() throws IOException {
        String source = readSource();
        String toast = between(source,
                "private static void showClipboardToast(",
                "public boolean sendClipboard(boolean force)");

        assertTrue(toast.contains("WeakReference<Game>"));
        assertTrue(toast.contains("activity.runOnUiThreadIfActive("));
        assertTrue(toast.contains("if (!lifecycle.isActive(token))"));
        assertFalse(toast.contains("activity.runOnUiThread("));
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
