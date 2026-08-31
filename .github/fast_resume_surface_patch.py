from pathlib import Path


def read(path):
    p = Path(path)
    data = p.read_bytes()
    newline = '\r\n' if b'\r\n' in data else '\n'
    return data.decode('utf-8').replace('\r\n', '\n'), newline


def write(path, text, newline='\n'):
    Path(path).write_bytes(text.replace('\n', newline).encode('utf-8'))


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


policy = 'app/src/main/java/com/limelight/preferences/BackgroundStreamingPolicy.java'
replace_once(
    policy,
    '''    public static boolean shouldUseFastResume(String mode,
                                               boolean finishing,
                                               boolean changingConfigurations,
                                               boolean inPictureInPicture,
                                               boolean externalDisplay) {''',
    '''    public static boolean shouldArmFastResumeBeforeSurfaceLoss(String mode,
                                                               boolean finishing,
                                                               boolean changingConfigurations,
                                                               boolean pipTransitionExpected,
                                                               boolean externalDisplay,
                                                               boolean multiWindow) {
        return isFastResume(mode) &&
                !finishing &&
                !changingConfigurations &&
                !pipTransitionExpected &&
                !externalDisplay &&
                !multiWindow;
    }

    public static boolean shouldUseFastResume(String mode,
                                               boolean finishing,
                                               boolean changingConfigurations,
                                               boolean inPictureInPicture,
                                               boolean externalDisplay) {''')

policy_test = 'app/src/test/java/com/limelight/preferences/BackgroundStreamingPolicyTest.java'
replace_once(
    policy_test,
    '''    @Test
    public void fastResumeOnlyOwnsOrdinaryBackgroundStop() {''',
    '''    @Test
    public void fastResumeArmingAvoidsPipAndVisibleMultiWindowTransitions() {
        assertTrue(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, true, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, false, false, true));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_DISABLED,
                false, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                true, false, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, true, false, false, false));
        assertFalse(BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                BackgroundStreamingPolicy.MODE_FAST_RESUME,
                false, false, false, true, false));
    }

    @Test
    public void fastResumeOnlyOwnsOrdinaryBackgroundStop() {''')

controller = 'app/src/main/java/com/limelight/binding/input/ControllerHandler.java'
replace_once(
    controller,
    '''    private boolean stopped = false;
    private boolean suspendedForReconnect = false;''',
    '''    private volatile boolean stopped = false;
    private volatile boolean suspendedForReconnect = false;''')
replace_once(
    controller,
    '''    public void resumeAfterReconnect() {
        if (stopped || !suspendedForReconnect) {
            return;
        }

        suspendedForReconnect = false;
        inputManager.registerInputDeviceListener(this, null);
        enableSensors();
    }''',
    '''    public void resumeAfterReconnect() {
        if (stopped || !suspendedForReconnect) {
            return;
        }

        // Device removal callbacks are not delivered while suspended. Reconcile retained
        // contexts before accepting new input so a controller unplugged in the background does
        // not leave a stale controller number/context behind after Fast Resume.
        for (int i = inputDeviceContexts.size() - 1; i >= 0; i--) {
            int deviceId = inputDeviceContexts.keyAt(i);
            if (InputDevice.getDevice(deviceId) == null) {
                onInputDeviceRemoved(deviceId);
            }
        }

        suspendedForReconnect = false;
        inputManager.registerInputDeviceListener(this, null);
        enableSensors();
    }''')

game = 'app/src/main/java/com/limelight/Game.java'
replace_once(
    game,
    '''        if (!isFinishing() && prefConfig != null &&
                BackgroundStreamingPolicy.isFastResume(prefConfig.backgroundStreamingMode) &&
                !isChangingConfigurations() && !isOnExternalDisplay()) {
            // Arm before Surface destruction so an expected graceful connection teardown cannot
            // race ahead of onStop(). PiP entry explicitly clears this arm below.
            fastResumeLifecycleArmed = true;
        }''',
    '''        boolean multiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode();
        boolean pipTransitionExpected = autoEnterPip || isCurrentlyInPip();
        if (prefConfig != null && BackgroundStreamingPolicy.shouldArmFastResumeBeforeSurfaceLoss(
                prefConfig.backgroundStreamingMode,
                isFinishing(), isChangingConfigurations(), pipTransitionExpected,
                isOnExternalDisplay(), multiWindow)) {
            // Arm before Surface destruction so an expected graceful connection teardown cannot
            // race ahead of onStop(). PiP and visible multi-window transitions deliberately do not
            // arm Fast Resume because the stream should remain live in those modes.
            fastResumeLifecycleArmed = true;
        } else if (!fastResumeBackgrounded && !fastResumeReconnectPending) {
            fastResumeLifecycleArmed = false;
        }''')
replace_once(
    game,
    '''    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        updatePipOverlayState(isInPictureInPictureMode);''',
    '''    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            // PiP owns this transition. Do not let an earlier pause callback reinterpret it as
            // Fast Resume if the platform changes lifecycle ordering on a particular device.
            fastResumeLifecycleArmed = false;
        }
        updatePipOverlayState(isInPictureInPictureMode);''')
replace_once(
    game,
    '''        if (attemptedConnection) {
            // Let the decoder know immediately that the surface is gone
            decoderRenderer.prepareForStop();

            if (connected) {
                stopConnection();
            }
        }''',
    '''        if (attemptedConnection) {
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
        }''')
