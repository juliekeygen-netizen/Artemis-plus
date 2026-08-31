from pathlib import Path


def read(path):
    p = Path(path)
    data = p.read_bytes()
    nl = '\r\n' if b'\r\n' in data else '\n'
    return data.decode('utf-8').replace('\r\n', '\n'), nl


def write(path, text, nl='\n'):
    Path(path).write_bytes(text.replace('\n', nl).encode('utf-8'))


def replace_once(path, old, new):
    text, nl = read(path)
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old[:180]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'anchor not unique: {path}: count={text.count(old)}')
    write(path, text.replace(old, new, 1), nl)


# ---- Settings UI ----
arrays = 'app/src/main/res/values/arrays.xml'
replace_once(arrays,
'''    <string-array name="background_streaming_mode_names">
        <item>Disabled</item>
        <item>Fast resume</item>
    </string-array>
    <string-array name="background_streaming_mode_values" translatable="false">
        <item>disabled</item>
        <item>fast_resume</item>
    </string-array>''',
'''    <string-array name="background_streaming_mode_names">
        <item>Disabled</item>
        <item>Fast resume</item>
        <item>Keep connection alive (experimental)</item>
    </string-array>
    <string-array name="background_streaming_mode_values" translatable="false">
        <item>disabled</item>
        <item>fast_resume</item>
        <item>keep_alive</item>
    </string-array>''')

prefs_xml = 'app/src/main/res/xml/preferences.xml'
replace_once(prefs_xml,
'''            android:title="Background streaming"
            android:summary="Fast resume closes the client stream while Artemis is in the background, leaves the host app running, and reconnects when you return."''',
'''            android:title="Background streaming"
            android:summary="Fast resume disconnects only the client and reconnects on return. Keep connection alive (experimental) keeps the network stream and decoder running with a foreground service; unsupported Surface handoff falls back to Fast Resume."''')
replace_once(prefs_xml,
'''            android:title="Background timeout"
            android:summary="How long Fast Resume keeps this stream screen ready to reconnect before Artemis fully closes it."''',
'''            android:title="Background timeout"
            android:summary="How long Fast Resume or Keep Connection Alive may remain in the background before Artemis fully closes the stream. Choose Until manually disconnected for no timeout."''')

settings = 'app/src/main/java/com/limelight/preferences/StreamSettings.java'
replace_once(settings,
'''                backgroundTimeout.setEnabled(BackgroundStreamingPolicy.isFastResume(backgroundMode.getValue()));
                backgroundMode.setOnPreferenceChangeListener((preference, newValue) -> {
                    backgroundTimeout.setEnabled(
                            BackgroundStreamingPolicy.isFastResume(String.valueOf(newValue)));
                    return true;
                });''',
'''                backgroundTimeout.setEnabled(
                        BackgroundStreamingPolicy.usesBackgroundTimeout(backgroundMode.getValue()));
                backgroundMode.setOnPreferenceChangeListener((preference, newValue) -> {
                    backgroundTimeout.setEnabled(
                            BackgroundStreamingPolicy.usesBackgroundTimeout(String.valueOf(newValue)));
                    return true;
                });''')

# ---- Foreground service permissions/declaration ----
manifest = 'app/src/main/AndroidManifest.xml'
replace_once(manifest,
'''    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />''',
'''    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />''')
replace_once(manifest,
'''        <service
            android:name=".discovery.DiscoveryService"
            android:label="mDNS PC Auto-Discovery Service" />''',
'''        <service
            android:name=".StreamKeepAliveService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
        <service
            android:name=".discovery.DiscoveryService"
            android:label="mDNS PC Auto-Discovery Service" />''')

# ---- Dynamic MediaCodec output Surface switching ----
renderer = 'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java'
replace_once(renderer,
'''    public void setRenderTarget(Surface renderTarget) {
        this.renderTarget = renderTarget;
    }
''',
'''    public void setRenderTarget(Surface renderTarget) {
        this.renderTarget = renderTarget;
    }

    /**
     * Switch a running decoder between the visible Activity Surface and the drained headless
     * Surface used by Keep Connection Alive. Some vendor codecs reject a replacement Surface;
     * callers must treat false as a signal to fall back to Fast Resume.
     */
    @TargetApi(Build.VERSION_CODES.M)
    public boolean switchOutputSurface(Surface newSurface) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || stopping || videoDecoder == null ||
                newSurface == null || !newSurface.isValid()) {
            return false;
        }
        try {
            videoDecoder.setOutputSurface(newSurface);
            renderTarget = newSurface;
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            LimeLog.warning("Decoder rejected output Surface switch: " + e.getMessage());
            return false;
        }
    }
''')

# ---- Game lifecycle integration ----
game = 'app/src/main/java/com/limelight/Game.java'
replace_once(game,
'''import com.limelight.binding.video.MediaCodecDecoderRenderer;''',
'''import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.HeadlessVideoSurface;''')

replace_once(game,
'''    private boolean fastResumeLifecycleArmed;
    private boolean fastResumeBackgrounded;
    private boolean fastResumeReconnectPending;
    private boolean reportedShortcutUsage;''',
'''    private boolean fastResumeLifecycleArmed;
    private boolean fastResumeBackgrounded;
    private boolean fastResumeReconnectPending;
    private boolean keepAliveLifecycleArmed;
    private boolean keepAliveBackgrounded;
    private boolean keepAliveReturnPending;
    private boolean keepAliveFallbackToFastResume;
    private boolean keepAliveServiceStarted;
    private HeadlessVideoSurface keepAliveSurface;
    private boolean reportedShortcutUsage;''')

replace_once(game,
'''    private final Runnable fastResumeTimeoutRunnable = () -> {
        if (!fastResumeBackgrounded || isFinishing()) {
            return;
        }
        LimeLog.info("Fast Resume background timeout expired");
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        finish();
    };
    private boolean isHidingOverlays;''',
'''    private final Runnable fastResumeTimeoutRunnable = () -> {
        if (!fastResumeBackgrounded || isFinishing()) {
            return;
        }
        LimeLog.info("Fast Resume background timeout expired");
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        finish();
    };
    private final Runnable keepAliveTimeoutRunnable = () -> {
        if (!keepAliveBackgrounded || isFinishing()) {
            return;
        }
        LimeLog.info("Keep Alive background timeout expired");
        finishKeepAliveSession();
    };
    private boolean isHidingOverlays;''')

replace_once(game,
'''            } else {
                startFastResumeReconnectIfReady();
            }
        });''',
'''            } else {
                if (!restoreKeepAliveVisibleSurfaceIfReady()) {
                    startFastResumeReconnectIfReady();
                }
            }
        });''')

# Insert Keep Alive cleanup into onDestroy before irreversible controller teardown.
replace_once(game,
'''        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        stopListeningForExternalDisplayRemoval();''',
'''        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        stopKeepAliveService();
        closeKeepAliveSurface();
        stopListeningForExternalDisplayRemoval();''')

# Make Fast Resume recognize a session-level Keep Alive downgrade.
replace_once(game,
'''        return fastResumeLifecycleArmed && prefConfig != null &&
                BackgroundStreamingPolicy.shouldUseFastResume(
                        prefConfig.backgroundStreamingMode,
                        isFinishing(),''',
'''        String effectiveMode = keepAliveFallbackToFastResume ?
                BackgroundStreamingPolicy.MODE_FAST_RESUME : prefConfig.backgroundStreamingMode;
        return fastResumeLifecycleArmed && prefConfig != null &&
                BackgroundStreamingPolicy.shouldUseFastResume(
                        effectiveMode,
                        isFinishing(),''')

# Add Keep Alive lifecycle helpers before Wi-Fi helpers.
replace_once(game,
'''    private void releaseStreamingWifiLocks() {''',
'''    private boolean isKeepAliveSupportedForSession() {
        return prefConfig != null && !keepAliveFallbackToFastResume &&
                BackgroundStreamingPolicy.isKeepAlivePlatformSupported(
                        Build.VERSION.SDK_INT, prefConfig.renderMode);
    }

    private boolean shouldUseKeepAliveForBackgroundStop() {
        return keepAliveLifecycleArmed && prefConfig != null &&
                BackgroundStreamingPolicy.shouldUseKeepAlive(
                        prefConfig.backgroundStreamingMode,
                        isKeepAliveSupportedForSession(),
                        isFinishing(), isChangingConfigurations(),
                        isCurrentlyInPip(), isOnExternalDisplay());
    }

    private void stopKeepAliveService() {
        if (keepAliveServiceStarted) {
            StreamKeepAliveService.stop(this);
            keepAliveServiceStarted = false;
        }
    }

    private void closeKeepAliveSurface() {
        if (keepAliveSurface != null) {
            keepAliveSurface.close();
            keepAliveSurface = null;
        }
    }

    private void downgradeKeepAliveToFastResume(String reason) {
        LimeLog.warning("Keep Connection Alive unavailable; falling back to Fast Resume: " + reason);
        keepAliveFallbackToFastResume = true;
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        stopKeepAliveService();
        closeKeepAliveSurface();
        fastResumeLifecycleArmed = true;
    }

    private void enterKeepAliveBackground() {
        if (keepAliveBackgrounded) {
            return;
        }
        LimeLog.info("Entering Keep Connection Alive background state");
        keepAliveLifecycleArmed = true;
        keepAliveBackgrounded = true;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        setInputGrabState(false);
        if (controllerHandler != null) {
            controllerHandler.suspendForReconnect();
        }
        decoderRenderer.notifyVideoBackground();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (prefConfig.backgroundStreamingTimeoutMs > 0) {
            timerHandler.postDelayed(keepAliveTimeoutRunnable, prefConfig.backgroundStreamingTimeoutMs);
        }
    }

    private void finishKeepAliveSession() {
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        closeKeepAliveSurface();
        stopConnection();
        stopKeepAliveService();
        finish();
    }

    private boolean restoreKeepAliveVisibleSurfaceIfReady() {
        if (!keepAliveBackgrounded || !keepAliveReturnPending || streamContainer == null) {
            return false;
        }
        Surface visibleSurface = streamContainer.getSurface();
        if (visibleSurface == null || !visibleSurface.isValid()) {
            return true;
        }

        if (keepAliveSurface != null && !decoderRenderer.switchOutputSurface(visibleSurface)) {
            // We still have a valid drained output Surface, so stop the live transport cleanly and
            // reuse the already-hardened Fast Resume reconnect path instead of risking a dead codec.
            decoderRenderer.prepareForStop();
            downgradeKeepAliveToFastResume("decoder rejected return to visible Surface");
            fastResumeBackgrounded = true;
            fastResumeReconnectPending = true;
            displayedFailureDialog = false;
            stopConnection(true);
            decoderRenderer.setRenderTarget(visibleSurface);
            startFastResumeReconnectIfReady();
            return true;
        }

        closeKeepAliveSurface();
        keepAliveLifecycleArmed = false;
        keepAliveBackgrounded = false;
        keepAliveReturnPending = false;
        timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
        decoderRenderer.notifyVideoForeground();
        if (controllerHandler != null) {
            controllerHandler.resumeAfterReconnect();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        timerHandler.postDelayed(() -> {
            if (!isFinishing() && connected) {
                setInputGrabState(true);
            }
        }, 300);
        hideSystemUi(100);
        LimeLog.info("Keep Connection Alive returned to visible Surface without reconnecting");
        return true;
    }

    private void releaseStreamingWifiLocks() {''')

# onResume: Keep Alive return takes precedence over Fast Resume.
replace_once(game,
'''        if (fastResumeBackgrounded) {
            LimeLog.info("Returning from Fast Resume background state");''',
'''        if (keepAliveBackgrounded) {
            LimeLog.info("Returning from Keep Connection Alive background state");
            timerHandler.removeCallbacks(keepAliveTimeoutRunnable);
            keepAliveReturnPending = true;
            restoreKeepAliveVisibleSurfaceIfReady();
        } else if (fastResumeBackgrounded) {
            LimeLog.info("Returning from Fast Resume background state");''')

# onPause: arm real Keep Alive first; unsupported/service-failed sessions downgrade to Fast Resume.
old_pause = '''        if (prefConfig != null && BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                prefConfig.backgroundStreamingMode,
                isFinishing(), isChangingConfigurations(), pipTransitionExpected,
                isOnExternalDisplay(), multiWindow)) {
            // Arm before Surface destruction so an expected graceful connection teardown cannot
            // race ahead of onStop(). PiP and visible multi-window transitions deliberately do not
            // arm Fast Resume because the stream should remain live in those modes.
            fastResumeLifecycleArmed = true;
        } else if (!fastResumeBackgrounded && !fastResumeReconnectPending) {
            fastResumeLifecycleArmed = false;
        }'''
new_pause = '''        boolean ordinaryKeepAliveTransition = prefConfig != null &&
                BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(
                        prefConfig.backgroundStreamingMode,
                        isKeepAliveSupportedForSession() && keepAliveServiceStarted,
                        isFinishing(), isChangingConfigurations(), pipTransitionExpected,
                        isOnExternalDisplay(), multiWindow);
        if (ordinaryKeepAliveTransition) {
            keepAliveLifecycleArmed = true;
            fastResumeLifecycleArmed = false;
        } else if (prefConfig != null && BackgroundStreamingPolicy.isKeepAlive(prefConfig.backgroundStreamingMode) &&
                !isFinishing() && !isChangingConfigurations() && !pipTransitionExpected &&
                !isOnExternalDisplay() && !multiWindow && !keepAliveBackgrounded) {
            // Old Android versions, stereo render mode, or a service-start failure use the safe
            // Fast Resume implementation rather than silently disabling background behavior.
            keepAliveFallbackToFastResume = true;
            keepAliveLifecycleArmed = false;
            fastResumeLifecycleArmed = true;
        } else if (prefConfig != null && BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                prefConfig.backgroundStreamingMode,
                isFinishing(), isChangingConfigurations(), pipTransitionExpected,
                isOnExternalDisplay(), multiWindow)) {
            fastResumeLifecycleArmed = true;
            keepAliveLifecycleArmed = false;
        } else if (!fastResumeBackgrounded && !fastResumeReconnectPending && !keepAliveBackgrounded) {
            fastResumeLifecycleArmed = false;
            keepAliveLifecycleArmed = false;
        }'''
replace_once(game, old_pause, new_pause)

# onStop: preserve live connection when the Keep Alive arm survived PiP/multi-window gating.
replace_once(game,
'''        if (shouldUseFastResumeForBackgroundStop()) {
            enterFastResumeBackground();
            return;
        }
        fastResumeLifecycleArmed = false;''',
'''        if (shouldUseKeepAliveForBackgroundStop()) {
            enterKeepAliveBackground();
            return;
        }
        if (shouldUseFastResumeForBackgroundStop()) {
            enterFastResumeBackground();
            return;
        }
        keepAliveLifecycleArmed = false;
        fastResumeLifecycleArmed = false;''')

# Any real transport stop means the foreground keep-alive claim is no longer true.
replace_once(game,
'''    private void stopConnection(boolean preserveControllerStateForReconnect) {
        if (connecting || connected) {''',
'''    private void stopConnection(boolean preserveControllerStateForReconnect) {
        stopKeepAliveService();
        if (connecting || connected) {''')

# Smart reconnect can use the live headless Surface while Keep Alive is backgrounded.
replace_once(game,
'''                        // Re-start the connection with the same parameters
                        if (conn != null && surfaceCreated && streamContainer.getSurface().isValid()) {
                            decoderRenderer.setRenderTarget(streamContainer.getSurface());
                            conn.start(new AndroidAudioRenderer(Game.this, prefConfig.playHostAudio),
                                    decoderRenderer, Game.this);''',
'''                        // Re-start the connection with the same parameters. Keep Alive may be
                        // decoding to a drained headless Surface while the Activity Surface is gone.
                        Surface reconnectSurface = null;
                        if ((keepAliveBackgrounded || keepAliveLifecycleArmed) &&
                                keepAliveSurface != null && keepAliveSurface.isValid()) {
                            reconnectSurface = keepAliveSurface.getSurface();
                        } else if (surfaceCreated && streamContainer.getSurface() != null &&
                                streamContainer.getSurface().isValid()) {
                            reconnectSurface = streamContainer.getSurface();
                        }
                        if (conn != null && reconnectSurface != null) {
                            decoderRenderer.setRenderTarget(reconnectSurface);
                            conn.start(new AndroidAudioRenderer(Game.this, prefConfig.playHostAudio),
                                    decoderRenderer, Game.this);''')

# connectionStarted: don't resume UI/input just because smart reconnect succeeded in the background.
replace_once(game,
'''                connected = true;
                connecting = false;
                if (controllerHandler != null) {
                    controllerHandler.resumeAfterReconnect();
                }
                fastResumeLifecycleArmed = false;
                fastResumeBackgrounded = false;
                fastResumeReconnectPending = false;
                timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
                if (reconnectOverlay != null) {
                    reconnectOverlay.hide();
                }
                updatePipAutoEnter();

                // Hide the mouse cursor now after a short delay.''',
'''                connected = true;
                connecting = false;
                boolean connectedWhileKeepAliveBackgrounded = keepAliveBackgrounded || keepAliveLifecycleArmed;
                if (!connectedWhileKeepAliveBackgrounded && controllerHandler != null) {
                    controllerHandler.resumeAfterReconnect();
                }
                if (!connectedWhileKeepAliveBackgrounded) {
                    fastResumeLifecycleArmed = false;
                    fastResumeBackgrounded = false;
                    fastResumeReconnectPending = false;
                    timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
                }
                if (reconnectOverlay != null) {
                    reconnectOverlay.hide();
                }
                updatePipAutoEnter();

                // Hide the mouse cursor now after a short delay.''')

replace_once(game,
'''                timerHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setInputGrabState(true);
                    }
                }, 500);

                // Keep the display on
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);''',
'''                if (!connectedWhileKeepAliveBackgrounded) {
                    timerHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setInputGrabState(true);
                        }
                    }, 500);

                    // Keep the display on while the stream is visible.
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }''')

# Start foreground service while Activity is definitely foreground. If unavailable, later pause
# transitions automatically downgrade to Fast Resume.
replace_once(game,
'''        if (prefConfig.usbDriver && !connectedToUsbDriverService) {''',
'''        if (BackgroundStreamingPolicy.isKeepAlive(prefConfig.backgroundStreamingMode) &&
                isKeepAliveSupportedForSession() && !keepAliveServiceStarted) {
            keepAliveServiceStarted = StreamKeepAliveService.start(this);
            if (!keepAliveServiceStarted) {
                keepAliveFallbackToFastResume = true;
            }
        }

        if (prefConfig.usbDriver && !connectedToUsbDriverService) {''')

# Surface destruction: hand the running codec to the drained Surface before Android destroys the
# visible one. Any vendor rejection falls through to the proven Fast Resume teardown path.
old_surface = '''        if (attemptedConnection) {
            // Let the decoder know immediately that the surface is gone.
            decoderRenderer.prepareForStop();

            // Surface destruction can occur after onPause() but before onStop(). If Fast Resume
            // was armed in onPause(), preserve controller contexts here too; otherwise this early
            // lifecycle callback would accidentally take the terminal Disconnect/Quit path before
            // onStop() gets a chance to park the stream.
            boolean preserveForFastResume = fastResumeLifecycleArmed ||
                    fastResumeBackgrounded || fastResumeReconnectPending;
            if (connecting || connected) {
                stopConnection(preserveForFastResume);
            }
        }'''
new_surface = '''        if (attemptedConnection) {
            if (keepAliveLifecycleArmed && connected && isKeepAliveSupportedForSession()) {
                keepAliveSurface = HeadlessVideoSurface.create(displayWidth, displayHeight);
                if (keepAliveSurface != null && keepAliveSurface.isValid() &&
                        decoderRenderer.switchOutputSurface(keepAliveSurface.getSurface())) {
                    LimeLog.info("Keep Connection Alive switched decoder to headless Surface");
                    decoderRenderer.notifyVideoBackground();
                    if (controllerHandler != null) {
                        controllerHandler.suspendForReconnect();
                    }
                    setInputGrabState(false);
                    return;
                }

                downgradeKeepAliveToFastResume("decoder rejected headless Surface");
            }

            // Let the decoder know immediately that the surface is gone.
            decoderRenderer.prepareForStop();

            // Surface destruction can occur after onPause() but before onStop(). Preserve controller
            // contexts for Fast Resume, including a Keep Alive session that just downgraded.
            boolean preserveForFastResume = fastResumeLifecycleArmed ||
                    fastResumeBackgrounded || fastResumeReconnectPending;
            if (connecting || connected) {
                stopConnection(preserveForFastResume);
            }
        }'''
replace_once(game, old_surface, new_surface)

print('Keep Alive POC patch applied')
