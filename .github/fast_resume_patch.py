from pathlib import Path


def read(path):
    p = Path(path)
    data = p.read_bytes()
    newline = '\r\n' if b'\r\n' in data else '\n'
    return data.decode('utf-8').replace('\r\n', '\n'), newline


def write(path, text, newline='\n'):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(text.replace('\n', newline).encode('utf-8'))


def replace_once(path, old, new):
    text, newline = read(path)
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'anchor missing: {path}: {old[:180]!r}')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'anchor not unique: {path}: count={count}')
    write(path, text.replace(old, new, 1), newline)


arrays = 'app/src/main/res/values/arrays.xml'
replace_once(
    arrays,
    '    <string-array name="outside_stream_orientation_names">',
    '''    <string-array name="background_streaming_mode_names">
        <item>Disabled</item>
        <item>Fast resume</item>
    </string-array>
    <string-array name="background_streaming_mode_values" translatable="false">
        <item>disabled</item>
        <item>fast_resume</item>
    </string-array>
    <string-array name="background_streaming_timeout_names">
        <item>30 seconds</item>
        <item>2 minutes</item>
        <item>5 minutes</item>
        <item>Until manually disconnected</item>
    </string-array>
    <string-array name="background_streaming_timeout_values" translatable="false">
        <item>30000</item>
        <item>120000</item>
        <item>300000</item>
        <item>0</item>
    </string-array>

    <string-array name="outside_stream_orientation_names">''')

prefs_xml = 'app/src/main/res/xml/preferences.xml'
replace_once(
    prefs_xml,
    '''        <CheckBoxPreference
            android:defaultValue="false"
            android:key="checkbox_resume_without_confirm"
            android:summary="@string/summary_checkbox_resume_without_confirm"
            android:title="@string/title_checkbox_resume_without_confirm"
            app:iconSpaceReserved="false" />''',
    '''        <CheckBoxPreference
            android:defaultValue="false"
            android:key="checkbox_resume_without_confirm"
            android:summary="@string/summary_checkbox_resume_without_confirm"
            android:title="@string/title_checkbox_resume_without_confirm"
            app:iconSpaceReserved="false" />
        <ListPreference
            android:defaultValue="disabled"
            android:entries="@array/background_streaming_mode_names"
            android:entryValues="@array/background_streaming_mode_values"
            android:key="list_background_streaming_mode"
            android:title="Background streaming"
            android:summary="Fast resume closes the client stream while Artemis is in the background, leaves the host app running, and reconnects when you return."
            app:iconSpaceReserved="false" />
        <ListPreference
            android:defaultValue="120000"
            android:entries="@array/background_streaming_timeout_names"
            android:entryValues="@array/background_streaming_timeout_values"
            android:key="list_background_streaming_timeout"
            android:title="Background timeout"
            android:summary="How long Fast Resume keeps this stream screen ready to reconnect before Artemis fully closes it."
            app:iconSpaceReserved="false" />''')

config = 'app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java'
replace_once(
    config,
    '    private static final String BOTTOM_EDGE_START_GESTURE_PREF_STRING = "list_bottom_edge_start_gesture";',
    '''    private static final String BOTTOM_EDGE_START_GESTURE_PREF_STRING = "list_bottom_edge_start_gesture";
    static final String BACKGROUND_STREAMING_MODE_PREF_STRING = "list_background_streaming_mode";
    static final String BACKGROUND_STREAMING_TIMEOUT_PREF_STRING = "list_background_streaming_timeout";''')
replace_once(
    config,
    '    private static final String DEFAULT_BOTTOM_EDGE_START_GESTURE = "native";',
    '''    private static final String DEFAULT_BOTTOM_EDGE_START_GESTURE = "native";
    private static final String DEFAULT_BACKGROUND_STREAMING_MODE = BackgroundStreamingPolicy.MODE_DISABLED;
    private static final String DEFAULT_BACKGROUND_STREAMING_TIMEOUT = "120000";''')
replace_once(
    config,
    '''    public boolean enablePip;
    public String bottomEdgeStartGestureMode;''',
    '''    public boolean enablePip;
    public String bottomEdgeStartGestureMode;
    public String backgroundStreamingMode;
    public long backgroundStreamingTimeoutMs;''')
replace_once(
    config,
    '''        config.bottomEdgeStartGestureMode = prefs.getString(
                BOTTOM_EDGE_START_GESTURE_PREF_STRING, DEFAULT_BOTTOM_EDGE_START_GESTURE);''',
    '''        config.bottomEdgeStartGestureMode = prefs.getString(
                BOTTOM_EDGE_START_GESTURE_PREF_STRING, DEFAULT_BOTTOM_EDGE_START_GESTURE);
        config.backgroundStreamingMode = prefs.getString(
                BACKGROUND_STREAMING_MODE_PREF_STRING, DEFAULT_BACKGROUND_STREAMING_MODE);
        config.backgroundStreamingTimeoutMs = BackgroundStreamingPolicy.parseTimeoutMillis(
                prefs.getString(BACKGROUND_STREAMING_TIMEOUT_PREF_STRING,
                        DEFAULT_BACKGROUND_STREAMING_TIMEOUT));''')

settings = 'app/src/main/java/com/limelight/preferences/StreamSettings.java'
replace_once(
    settings,
    '            AppCompatActivity activity = (AppCompatActivity) requireActivity();',
    '''            ListPreference backgroundMode = findPreference(
                    PreferenceConfiguration.BACKGROUND_STREAMING_MODE_PREF_STRING);
            ListPreference backgroundTimeout = findPreference(
                    PreferenceConfiguration.BACKGROUND_STREAMING_TIMEOUT_PREF_STRING);
            if (backgroundMode != null && backgroundTimeout != null) {
                backgroundTimeout.setEnabled(BackgroundStreamingPolicy.isFastResume(backgroundMode.getValue()));
                backgroundMode.setOnPreferenceChangeListener((preference, newValue) -> {
                    backgroundTimeout.setEnabled(
                            BackgroundStreamingPolicy.isFastResume(String.valueOf(newValue)));
                    return true;
                });
            }

            AppCompatActivity activity = (AppCompatActivity) requireActivity();''')

renderer = 'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java'
replace_once(
    renderer,
    '''    public int setup(int format, int width, int height, int redrawRate) {
        this.targetFps = (redrawRate > 0 ? redrawRate : 60);''',
    '''    public int setup(int format, int width, int height, int redrawRate) {
        // setup() may follow prepareForStop() during a deliberate Fast Resume reconnect.
        // A fresh codec session must not inherit the old renderer's terminal stopping state.
        stopping = false;
        this.targetFps = (redrawRate > 0 ? redrawRate : 60);''')

game = 'app/src/main/java/com/limelight/Game.java'
replace_once(
    game,
    'import com.limelight.preferences.PreferenceConfiguration;',
    '''import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.BackgroundStreamingPolicy;''')
replace_once(
    game,
    '''    private boolean quitOnStop = false;
    private boolean isHidingOverlays;''',
    '''    private boolean quitOnStop = false;
    private boolean fastResumeLifecycleArmed;
    private boolean fastResumeBackgrounded;
    private boolean fastResumeReconnectPending;
    private boolean reportedShortcutUsage;
    private final Runnable fastResumeTimeoutRunnable = () -> {
        if (!fastResumeBackgrounded || isFinishing()) {
            return;
        }
        LimeLog.info("Fast Resume background timeout expired");
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        finish();
    };
    private boolean isHidingOverlays;''')
replace_once(
    game,
    '''        streamContainer.setOnSurfaceAvailable(() -> {
            if (!attemptedConnection) {
                LimeLog.info("Surface is available, starting connection...");
                attemptedConnection = true;

                // Der Decoder erhält die jeweils aktive Oberfläche vom Container
                decoderRenderer.setRenderTarget(streamContainer.getSurface());

                // Starten Sie die NvConnection
                conn.start(new AndroidAudioRenderer(Game.this, prefConfig.playHostAudio),
                        decoderRenderer, Game.this);
            }
        });''',
    '''        streamContainer.setOnSurfaceAvailable(() -> {
            if (!attemptedConnection) {
                LimeLog.info("Surface is available, starting connection...");
                attemptedConnection = true;

                // Der Decoder erhält die jeweils aktive Oberfläche vom Container
                decoderRenderer.setRenderTarget(streamContainer.getSurface());

                // Starten Sie die NvConnection
                conn.start(new AndroidAudioRenderer(Game.this, prefConfig.playHostAudio),
                        decoderRenderer, Game.this);
            } else {
                startFastResumeReconnectIfReady();
            }
        });''')
replace_once(
    game,
    '''        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }''',
    '        releaseStreamingWifiLocks();')

replace_once(
    game,
    '''    @Override
    protected void onPause() {
        if (isFinishing()) {''',
    '''    private boolean isCurrentlyInPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode();
    }

    private boolean shouldUseFastResumeForBackgroundStop() {
        return prefConfig != null && BackgroundStreamingPolicy.shouldUseFastResume(
                prefConfig.backgroundStreamingMode,
                isFinishing(),
                isChangingConfigurations(),
                isCurrentlyInPip(),
                isOnExternalDisplay());
    }

    private void releaseStreamingWifiLocks() {
        try {
            if (lowLatencyWifiLock != null && lowLatencyWifiLock.isHeld()) {
                lowLatencyWifiLock.release();
            }
            if (highPerfWifiLock != null && highPerfWifiLock.isHeld()) {
                highPerfWifiLock.release();
            }
        } catch (RuntimeException e) {
            LimeLog.warning("Failed to release streaming Wi-Fi lock: " + e.getMessage());
        }
    }

    private void reacquireStreamingWifiLocks() {
        try {
            if (highPerfWifiLock != null && !highPerfWifiLock.isHeld()) {
                highPerfWifiLock.acquire();
            }
            if (lowLatencyWifiLock != null && !lowLatencyWifiLock.isHeld()) {
                lowLatencyWifiLock.acquire();
            }
        } catch (RuntimeException e) {
            LimeLog.warning("Failed to reacquire streaming Wi-Fi lock: " + e.getMessage());
        }
    }

    private void enterFastResumeBackground() {
        if (fastResumeBackgrounded) {
            return;
        }

        LimeLog.info("Entering Fast Resume background state");
        fastResumeLifecycleArmed = true;
        fastResumeBackgrounded = true;
        fastResumeReconnectPending = false;
        displayedFailureDialog = true;
        timerHandler.removeCallbacks(fastResumeTimeoutRunnable);

        // The remote app intentionally stays running. Only the client-side stream is stopped.
        setInputGrabState(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopConnection();
        releaseStreamingWifiLocks();

        if (prefConfig.backgroundStreamingTimeoutMs > 0) {
            timerHandler.postDelayed(fastResumeTimeoutRunnable,
                    prefConfig.backgroundStreamingTimeoutMs);
        }
    }

    private void startFastResumeReconnectIfReady() {
        if (!fastResumeBackgrounded || !fastResumeReconnectPending || conn == null ||
                decoderRenderer == null || connected || connecting || streamContainer == null) {
            return;
        }

        Surface surface = streamContainer.getSurface();
        if (surface == null || !surface.isValid()) {
            return;
        }

        LimeLog.info("Fast Resume surface is ready, reconnecting stream");
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        displayedFailureDialog = false;
        decoderRenderer.setRenderTarget(surface);
        if (reconnectOverlay != null) {
            reconnectOverlay.show(1);
        }
        conn.start(new AndroidAudioRenderer(Game.this, prefConfig.playHostAudio),
                decoderRenderer, Game.this);
    }

    private void cancelFastResumeState() {
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        if (timerHandler != null) {
            timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (fastResumeBackgrounded) {
            LimeLog.info("Returning from Fast Resume background state");
            timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
            fastResumeReconnectPending = true;
            reacquireStreamingWifiLocks();
            startFastResumeReconnectIfReady();
        } else {
            fastResumeLifecycleArmed = false;
        }
    }

    @Override
    protected void onPause() {
        if (!isFinishing() && prefConfig != null &&
                BackgroundStreamingPolicy.isFastResume(prefConfig.backgroundStreamingMode) &&
                !isChangingConfigurations() && !isOnExternalDisplay()) {
            // Arm before Surface destruction so an expected graceful connection teardown cannot
            // race ahead of onStop(). PiP entry explicitly clears this arm below.
            fastResumeLifecycleArmed = true;
        }

        if (isFinishing()) {''')

replace_once(
    game,
    '''        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (virtualController != null) {''',
    '''        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (shouldUseFastResumeForBackgroundStop()) {
            enterFastResumeBackground();
            return;
        }
        fastResumeLifecycleArmed = false;

        if (virtualController != null) {''')

replace_once(
    game,
    '''    public void connectionTerminated(final int errorCode) {
        // For graceful termination or non-reconnectable errors, skip smart reconnect''',
    '''    public void connectionTerminated(final int errorCode) {
        // A graceful termination is expected while Fast Resume parks the client stream.
        // Do not let that callback finish the retained Activity or cancel its timeout.
        if (errorCode == MoonBridge.ML_ERROR_GRACEFUL_TERMINATION &&
                (fastResumeLifecycleArmed || fastResumeBackgrounded || fastResumeReconnectPending)) {
            LimeLog.info("Ignoring expected graceful termination for Fast Resume");
            return;
        }

        // For graceful termination or non-reconnectable errors, skip smart reconnect''')

replace_once(
    game,
    '''                connected = true;
                connecting = false;
                updatePipAutoEnter();''',
    '''                connected = true;
                connecting = false;
                fastResumeLifecycleArmed = false;
                fastResumeBackgrounded = false;
                fastResumeReconnectPending = false;
                timerHandler.removeCallbacks(fastResumeTimeoutRunnable);
                if (reconnectOverlay != null) {
                    reconnectOverlay.hide();
                }
                updatePipAutoEnter();''')

replace_once(
    game,
    '''        if (prefConfig.usbDriver) {
            // Start the USB driver
            bindService(new Intent(this, UsbDriverService.class),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
        }

        // Report this shortcut being used (off the main thread to prevent ANRs)
        ComputerDetails computer = new ComputerDetails();
        computer.name = pcName;
        computer.uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
        ShortcutHelper shortcutHelper = new ShortcutHelper(this);
        shortcutHelper.reportComputerShortcutUsed(computer);
        if (appName != null) {
            // This may be null if launched from the "Resume Session" PC context menu item
            shortcutHelper.reportGameLaunched(computer, app);
        }''',
    '''        if (prefConfig.usbDriver && !connectedToUsbDriverService) {
            // Start the USB driver once. Fast Resume and smart reconnect reuse the existing binding.
            bindService(new Intent(this, UsbDriverService.class),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
        }

        // A reconnect is not a new shortcut launch. Report usage only for the first connection.
        if (!reportedShortcutUsage) {
            reportedShortcutUsage = true;
            ComputerDetails computer = new ComputerDetails();
            computer.name = pcName;
            computer.uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
            ShortcutHelper shortcutHelper = new ShortcutHelper(this);
            shortcutHelper.reportComputerShortcutUsed(computer);
            if (appName != null) {
                // This may be null if launched from the "Resume Session" PC context menu item
                shortcutHelper.reportGameLaunched(computer, app);
            }
        }''')

replace_once(
    game,
    '''    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface destroyed before creation!");
        }

        if (attemptedConnection) {''',
    '''    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface destroyed before creation!");
        }
        surfaceCreated = false;

        if (attemptedConnection) {''')

replace_once(
    game,
    '''        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }
    }

    @Override
    public void surfaceDestroyed''',
    '''        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }

        startFastResumeReconnectIfReady();
    }

    @Override
    public void surfaceDestroyed''')

replace_once(
    game,
    '''        if (inPip) {
            boolean entered = pipOverlayState.enter(''',
    '''        if (inPip) {
            // PiP is still an active stream, not a Fast Resume background stop.
            fastResumeLifecycleArmed = false;
            boolean entered = pipOverlayState.enter(''')

replace_once(
    game,
    '''    public void disconnect() {
        if (prefConfig.smartClipboardSync) {''',
    '''    public void disconnect() {
        cancelFastResumeState();
        if (prefConfig.smartClipboardSync) {''')

replace_once(
    game,
    '''        builder.setPositiveButton(getString(R.string.yes), (dialog, which) -> {
            quitOnStop = true;''',
    '''        builder.setPositiveButton(getString(R.string.yes), (dialog, which) -> {
            cancelFastResumeState();
            quitOnStop = true;''')

replace_once(
    game,
    '''        timerHandler.removeCallbacksAndMessages(null);
        stopListeningForExternalDisplayRemoval();''',
    '''        timerHandler.removeCallbacksAndMessages(null);
        fastResumeLifecycleArmed = false;
        fastResumeBackgrounded = false;
        fastResumeReconnectPending = false;
        stopListeningForExternalDisplayRemoval();''')
