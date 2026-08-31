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

# The service request can succeed before Service.onStartCommand() has successfully called
# startForeground(). Only the explicit readiness signal is strong enough to arm Keep Alive.
replace_once(game,
'''                        isKeepAliveSupportedForSession() && keepAliveServiceStarted,
                        isFinishing(), isChangingConfigurations(), pipTransitionExpected,''',
'''                        isKeepAliveSupportedForSession() && keepAliveServiceStarted &&
                                StreamKeepAliveService.isForegroundActive(),
                        isFinishing(), isChangingConfigurations(), pipTransitionExpected,''')

# Avoid dereferencing prefConfig before the defensive null check in lifecycle helpers.
replace_once(game,
'''    private boolean shouldUseFastResumeForBackgroundStop() {
        // onPause() is the authoritative transition gate because Surface loss may happen before
        // onStop(). If PiP/multi-window revoked the arm, onStop() must not reinterpret the same
        // transition as Fast Resume after a terminal Surface path has already begun.
        String effectiveMode = keepAliveFallbackToFastResume ?
                BackgroundStreamingPolicy.MODE_FAST_RESUME : prefConfig.backgroundStreamingMode;
        return fastResumeLifecycleArmed && prefConfig != null &&
                BackgroundStreamingPolicy.shouldUseFastResume(''',
'''    private boolean shouldUseFastResumeForBackgroundStop() {
        // onPause() is the authoritative transition gate because Surface loss may happen before
        // onStop(). If PiP/multi-window revoked the arm, onStop() must not reinterpret the same
        // transition as Fast Resume after a terminal Surface path has already begun.
        if (prefConfig == null) {
            return false;
        }
        String effectiveMode = keepAliveFallbackToFastResume ?
                BackgroundStreamingPolicy.MODE_FAST_RESUME : prefConfig.backgroundStreamingMode;
        return fastResumeLifecycleArmed &&
                BackgroundStreamingPolicy.shouldUseFastResume(''')

# If all reconnect attempts fail while headless, this stream is terminal. Clear Keep Alive state
# before stopping ControllerHandler so a later onResume() cannot try to restore a released codec
# and then discover that controller state was already permanently destroyed.
replace_once(game,
'''            @Override
            public void run() {
                // Let the display go to sleep now
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // Stop processing controller input
                controllerHandler.stop();''',
'''            @Override
            public void run() {
                // Let the display go to sleep now
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                if (keepAliveBackgrounded || keepAliveLifecycleArmed || keepAliveReturnPending) {
                    timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
                    keepAliveLifecycleArmed = false;
                    keepAliveBackgrounded = false;
                    keepAliveReturnPending = false;
                    stopKeepAliveService();
                    if (decoderRenderer != null) {
                        decoderRenderer.prepareForStop();
                    }
                    closeKeepAliveSurface();
                }

                // Stop processing controller input
                controllerHandler.stop();''')

print('Keep Alive terminal-state audit patch applied')
