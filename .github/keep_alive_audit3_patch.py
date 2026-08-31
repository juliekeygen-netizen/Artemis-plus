from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    data = p.read_bytes()
    nl = '\r\n' if b'\r\n' in data else '\n'
    text = data.decode('utf-8').replace('\r\n', '\n')
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old[:180]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'anchor not unique: {path}: count={text.count(old)}')
    p.write_bytes(text.replace(old, new, 1).replace('\n', nl).encode('utf-8'))


game = 'app/src/main/java/com/limelight/Game.java'

# Keep Connection Alive deliberately allows the display to turn off. A foreground service raises
# process importance but does not keep the CPU awake; hold the minimum PARTIAL_WAKE_LOCK only while
# the stream is actually backgrounded. WAKE_LOCK is already declared in the manifest.
replace_once(game,
'''import android.os.PersistableBundle;
import android.os.VibrationEffect;''',
'''import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.VibrationEffect;''')

replace_once(game,
'''    private boolean keepAliveFallbackToFastResume;
    private boolean keepAliveServiceStarted;
    private HeadlessVideoSurface keepAliveSurface;
    private boolean reportedShortcutUsage;''',
'''    private boolean keepAliveFallbackToFastResume;
    private boolean keepAliveServiceStarted;
    private HeadlessVideoSurface keepAliveSurface;
    private PowerManager.WakeLock keepAliveCpuWakeLock;
    private boolean reportedShortcutUsage;''')

replace_once(game,
'''    private void stopKeepAliveService() {
        if (keepAliveServiceStarted) {
            StreamKeepAliveService.stop(this);
            keepAliveServiceStarted = false;
        }
    }

    private void closeKeepAliveSurface() {''',
'''    private void stopKeepAliveService() {
        if (keepAliveServiceStarted) {
            StreamKeepAliveService.stop(this);
            keepAliveServiceStarted = false;
        }
    }

    private void acquireKeepAliveCpuWakeLock() {
        if (keepAliveCpuWakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                keepAliveCpuWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        getPackageName() + ":KeepConnectionAlive");
                keepAliveCpuWakeLock.setReferenceCounted(false);
            }
        }
        if (keepAliveCpuWakeLock != null && !keepAliveCpuWakeLock.isHeld()) {
            try {
                if (prefConfig != null && prefConfig.backgroundStreamingTimeoutMs > 0) {
                    keepAliveCpuWakeLock.acquire(prefConfig.backgroundStreamingTimeoutMs + 15_000L);
                } else {
                    keepAliveCpuWakeLock.acquire();
                }
            } catch (RuntimeException e) {
                LimeLog.warning("Unable to acquire Keep Alive CPU wake lock: " + e.getMessage());
            }
        }
    }

    private void releaseKeepAliveCpuWakeLock() {
        if (keepAliveCpuWakeLock != null && keepAliveCpuWakeLock.isHeld()) {
            try {
                keepAliveCpuWakeLock.release();
            } catch (RuntimeException e) {
                LimeLog.warning("Unable to release Keep Alive CPU wake lock: " + e.getMessage());
            }
        }
    }

    private void closeKeepAliveSurface() {''')

# A downgrade must not blindly close a headless Surface because one caller can still have the
# running MediaCodec attached to it. Individual callers close it only once it is known to be unused.
replace_once(game,
'''        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        stopKeepAliveService();
        closeKeepAliveSurface();
        fastResumeLifecycleArmed = true;
    }

    private void enterKeepAliveBackground() {''',
'''        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        releaseKeepAliveCpuWakeLock();
        stopKeepAliveService();
        fastResumeLifecycleArmed = true;
    }

    private void enterKeepAliveBackground() {''')

replace_once(game,
'''        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        setInputGrabState(false);''',
'''        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        acquireKeepAliveCpuWakeLock();
        setInputGrabState(false);''')

# Graceful timeout teardown must wait for NvConnection.stop() to finish before destroying the
# Surface still owned by MediaCodec. Mark the termination as expected so its callback cannot race
# the explicit completion path.
replace_once(game,
'''    private void finishKeepAliveSession() {
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        if (decoderRenderer != null && (connecting || connected)) {
            decoderRenderer.prepareForStop();
        }
        stopConnection();
        closeKeepAliveSurface();
        stopKeepAliveService();
        finish();
    }''',
'''    private void finishKeepAliveSession() {
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        displayedFailureDialog = true;
        if (decoderRenderer != null && (connecting || connected)) {
            decoderRenderer.prepareForStop();
        }
        stopConnection(false, () -> {
            closeKeepAliveSurface();
            releaseKeepAliveCpuWakeLock();
            stopKeepAliveService();
            if (!isFinishing()) {
                finish();
            }
        });
    }''')

# Returning from Keep Alive releases the CPU lock as soon as the visible stream is restored.
replace_once(game,
'''        closeKeepAliveSurface();
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        decoderRenderer.notifyVideoForeground();''',
'''        closeKeepAliveSurface();
        releaseKeepAliveCpuWakeLock();
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        decoderRenderer.notifyVideoForeground();''')

# If switching back to the visible Surface fails, leave the currently attached headless consumer
# alive until the old NvConnection has synchronously finished its stop routine. Then reconnect using
# the already-hardened Fast Resume path.
replace_once(game,
'''            decoderRenderer.prepareForStop();
            downgradeKeepAliveToFastResume("decoder rejected return to visible Surface");
            fastResumeBackgrounded = true;
            fastResumeReconnectPending = true;
            displayedFailureDialog = false;
            stopConnection(true);
            decoderRenderer.setRenderTarget(visibleSurface);
            startFastResumeReconnectIfReady();
            return true;''',
'''            decoderRenderer.prepareForStop();
            downgradeKeepAliveToFastResume("decoder rejected return to visible Surface");
            fastResumeBackgrounded = true;
            fastResumeReconnectPending = true;
            displayedFailureDialog = true;
            stopConnection(true, () -> {
                closeKeepAliveSurface();
                displayedFailureDialog = false;
                decoderRenderer.setRenderTarget(visibleSurface);
                startFastResumeReconnectIfReady();
            });
            return true;''')

# If the headless Surface was rejected before MediaCodec ever adopted it, it is safe to close
# immediately. This also prevents a small ImageReader/thread leak on the fallback path.
replace_once(game,
'''                downgradeKeepAliveToFastResume("decoder rejected headless Surface");
            }

            // Let the decoder know immediately that the surface is gone.''',
'''                downgradeKeepAliveToFastResume("decoder rejected headless Surface");
                closeKeepAliveSurface();
            }

            // Let the decoder know immediately that the surface is gone.''')

# Provide an explicit completion point for the asynchronous connection stop. Existing callers keep
# their old behavior; Keep Alive teardown uses the callback to dispose Surface-owned resources only
# after moonlight-core and the decoder have finished stopping.
replace_once(game,
'''    private void stopConnection() {
        stopConnection(false);
    }

    private void stopConnection(boolean preserveControllerStateForReconnect) {
        stopKeepAliveService();
        if (connecting || connected) {''',
'''    private void stopConnection() {
        stopConnection(false, null);
    }

    private void stopConnection(boolean preserveControllerStateForReconnect) {
        stopConnection(preserveControllerStateForReconnect, null);
    }

    private void stopConnection(boolean preserveControllerStateForReconnect, Runnable onStopped) {
        stopKeepAliveService();
        if (connecting || connected) {''')

replace_once(game,
'''                    if (httpConn != null && quitOnStop) {
                        try {
                            sleep(1000);
                            httpConn.quitApp();
                            Game.this.runOnUiThread(() -> Toast.makeText(Game.this, Game.this.getResources().getString(R.string.applist_quit_success) + " " + appName, Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            Game.this.runOnUiThread(() -> Toast.makeText(Game.this, e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }
                }
            }.start();
        }
    }''',
'''                    if (httpConn != null && quitOnStop) {
                        try {
                            sleep(1000);
                            httpConn.quitApp();
                            Game.this.runOnUiThread(() -> Toast.makeText(Game.this, Game.this.getResources().getString(R.string.applist_quit_success) + " " + appName, Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            Game.this.runOnUiThread(() -> Toast.makeText(Game.this, e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }
                    if (onStopped != null) {
                        Game.this.runOnUiThread(onStopped);
                    }
                }
            }.start();
        } else if (onStopped != null) {
            runOnUiThread(onStopped);
        }
    }''')

# Normal successful reconnect after a Keep-Alive-to-Fast-Resume downgrade is a safe final point to
# release any stale headless consumer/wake lock left by an earlier failure path.
replace_once(game,
'''                if (!connectedWhileKeepAliveBackgrounded) {
                    fastResumeLifecycleArmed = false;
                    fastResumeBackgrounded = false;
                    fastResumeReconnectPending = false;
                    timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
                }''',
'''                if (!connectedWhileKeepAliveBackgrounded) {
                    fastResumeLifecycleArmed = false;
                    fastResumeBackgrounded = false;
                    fastResumeReconnectPending = false;
                    timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
                    closeKeepAliveSurface();
                    releaseKeepAliveCpuWakeLock();
                }''')

# The Activity is the owner of the lock. Always release it during final destruction, even if a
# vendor callback sequence skipped one of the normal return/timeout paths.
replace_once(game,
'''        stopKeepAliveService();
        closeKeepAliveSurface();
        stopListeningForExternalDisplayRemoval();''',
'''        stopKeepAliveService();
        releaseKeepAliveCpuWakeLock();
        closeKeepAliveSurface();
        stopListeningForExternalDisplayRemoval();''')

print('Keep Alive async teardown/wake-lock audit patch applied')
