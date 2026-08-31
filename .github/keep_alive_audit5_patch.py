from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    data = p.read_bytes()
    nl = '\r\n' if b'\r\n' in data else '\n'
    text = data.decode('utf-8').replace('\r\n', '\n')
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old[:220]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'anchor not unique: {path}: count={text.count(old)}')
    p.write_bytes(text.replace(old, new, 1).replace('\n', nl).encode('utf-8'))


game = 'app/src/main/java/com/limelight/Game.java'

# fastResumeLifecycleArmed is the authoritative per-transition signal. Keep Alive can intentionally
# arm it for one transition when the FGS is not ready yet, without permanently disabling Keep Alive.
# Treat an armed Keep Alive transition as Fast Resume even when the persistent setting remains
# keep_alive and keepAliveFallbackToFastResume is false.
replace_once(game,
'''        String effectiveMode = keepAliveFallbackToFastResume ?
                BackgroundStreamingPolicy.MODE_FAST_RESUME : prefConfig.backgroundStreamingMode;''',
'''        String effectiveMode = (keepAliveFallbackToFastResume ||
                (fastResumeLifecycleArmed &&
                        BackgroundStreamingPolicy.isKeepAlive(prefConfig.backgroundStreamingMode))) ?
                BackgroundStreamingPolicy.MODE_FAST_RESUME : prefConfig.backgroundStreamingMode;''')

# Downgrade changes policy state only. Resource lifetime belongs to the active transport and must
# not end until stopConnection's completion callback says moonlight-core/MediaCodec are finished.
replace_once(game,
'''        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        releaseKeepAliveCpuWakeLock();
        stopKeepAliveService();
        fastResumeLifecycleArmed = true;''',
'''        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        fastResumeLifecycleArmed = true;''')

# In the return-Surface failure path the visible Activity is already foreground. Once the old
# headless transport has fully stopped, release its CPU wake lock before starting Fast Resume.
replace_once(game,
'''            stopConnection(true, () -> {
                closeKeepAliveSurface();
                displayedFailureDialog = false;
                decoderRenderer.setRenderTarget(visibleSurface);
                startFastResumeReconnectIfReady();
            });''',
'''            stopConnection(true, () -> {
                closeKeepAliveSurface();
                releaseKeepAliveCpuWakeLock();
                displayedFailureDialog = false;
                decoderRenderer.setRenderTarget(visibleSurface);
                startFastResumeReconnectIfReady();
            });''')

print('Keep Alive fallback transition audit patch applied')
