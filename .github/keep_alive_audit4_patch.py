from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    data = p.read_bytes()
    nl = '\r\n' if b'\r\n' in data else '\n'
    text = data.decode('utf-8').replace('\r\n', '\n')
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old[:200]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'anchor not unique: {path}: count={text.count(old)}')
    p.write_bytes(text.replace(old, new, 1).replace('\n', nl).encode('utf-8'))


game = 'app/src/main/java/com/limelight/Game.java'

# A foreground service is part of the live transport lifetime. Do not remove it before the
# asynchronous NvConnection.stop() has completed, especially when the Activity itself is already
# being destroyed. Stop it on the same completion boundary as other transport-owned resources.
replace_once(game,
'''    private void stopConnection(boolean preserveControllerStateForReconnect, Runnable onStopped) {
        stopKeepAliveService();
        if (connecting || connected) {''',
'''    private void stopConnection(boolean preserveControllerStateForReconnect, Runnable onStopped) {
        if (connecting || connected) {''')

replace_once(game,
'''                    if (onStopped != null) {
                        Game.this.runOnUiThread(onStopped);
                    }
                }
            }.start();
        } else if (onStopped != null) {
            runOnUiThread(onStopped);
        }
    }''',
'''                    Game.this.runOnUiThread(() -> {
                        stopKeepAliveService();
                        if (onStopped != null) {
                            onStopped.run();
                        }
                    });
                }
            }.start();
        } else {
            stopKeepAliveService();
            if (onStopped != null) {
                runOnUiThread(onStopped);
            }
        }
    }''')

# If Android destroys the retained Activity while a Keep Alive transport is still live, defer the
# headless Surface and CPU wake-lock release until conn.stop() has really returned. This avoids a
# decoder writing into a closed consumer and keeps the CPU alive for the final network teardown.
replace_once(game,
'''        timerHandler.removeCallbacksAndMessages(null);
        if ((keepAliveServiceStarted || keepAliveSurface != null || keepAliveBackgrounded ||
                keepAliveLifecycleArmed || keepAliveReturnPending) && (connecting || connected)) {
            if (decoderRenderer != null) {
                decoderRenderer.prepareForStop();
            }
            stopConnection();
        }
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        stopKeepAliveService();
        releaseKeepAliveCpuWakeLock();
        closeKeepAliveSurface();
        stopListeningForExternalDisplayRemoval();''',
'''        timerHandler.removeCallbacksAndMessages(null);
        boolean deferKeepAliveResourceRelease =
                (keepAliveServiceStarted || keepAliveSurface != null || keepAliveBackgrounded ||
                        keepAliveLifecycleArmed || keepAliveReturnPending) && (connecting || connected);
        if (deferKeepAliveResourceRelease) {
            if (decoderRenderer != null) {
                decoderRenderer.prepareForStop();
            }
            stopConnection(false, () -> {
                releaseKeepAliveCpuWakeLock();
                closeKeepAliveSurface();
            });
        }
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        if (!deferKeepAliveResourceRelease) {
            stopKeepAliveService();
            releaseKeepAliveCpuWakeLock();
            closeKeepAliveSurface();
        }
        stopListeningForExternalDisplayRemoval();''')

# A service that has merely been requested but has not yet entered foreground is a transient
# readiness race, not proof the device is incompatible. Use Fast Resume for that transition but
# allow a later reconnect/background attempt to try Keep Alive again. Permanent per-session fallback
# remains reserved for unsupported render/platform combinations or a start request that failed.
replace_once(game,
'''        } else if (prefConfig != null && BackgroundStreamingPolicy.isKeepAlive(prefConfig.backgroundStreamingMode) &&
                !isFinishing() && !isChangingConfigurations() && !pipTransitionExpected &&
                !isOnExternalDisplay() && !multiWindow && !keepAliveBackgrounded) {
            // Old Android versions, stereo render mode, or a service-start failure use the safe
            // Fast Resume implementation rather than silently disabling background behavior.
            keepAliveFallbackToFastResume = true;
            keepAliveLifecycleArmed = false;
            fastResumeLifecycleArmed = true;''',
'''        } else if (prefConfig != null && BackgroundStreamingPolicy.isKeepAlive(prefConfig.backgroundStreamingMode) &&
                !isFinishing() && !isChangingConfigurations() && !pipTransitionExpected &&
                !isOnExternalDisplay() && !multiWindow && !keepAliveBackgrounded) {
            // Old Android versions, stereo render mode, or a service-start failure use Fast Resume.
            // If the service start was accepted but foreground promotion is merely still racing,
            // keep the mode eligible so a later reconnect can try Keep Alive again.
            if (!isKeepAliveSupportedForSession() || !keepAliveServiceStarted) {
                keepAliveFallbackToFastResume = true;
            }
            keepAliveLifecycleArmed = false;
            fastResumeLifecycleArmed = true;''')

print('Keep Alive final teardown/readiness audit patch applied')
