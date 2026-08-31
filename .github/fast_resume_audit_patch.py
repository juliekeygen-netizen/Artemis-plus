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


controller = 'app/src/main/java/com/limelight/binding/input/ControllerHandler.java'
replace_once(
    controller,
    '    private boolean stopped = false;',
    '''    private boolean stopped = false;
    private boolean suspendedForReconnect = false;''')
replace_once(
    controller,
    '''    public void stop() {
        if (stopped) {
            return;
        }

        // Stop new device contexts from being created or used
        stopped = true;

        // Cancel any pending stats overlay toggle
        selectL1HoldPending = false;
        mainThreadHandler.removeCallbacks(statsOverlayToggleRunnable);

        // Unregister our input device callbacks
        inputManager.unregisterInputDeviceListener(this);''',
    '''    public void suspendForReconnect() {
        if (stopped || suspendedForReconnect) {
            return;
        }

        // Block input packets while the stream transport is intentionally down, but keep
        // controller contexts intact so a Fast Resume reconnect can restore them immediately.
        suspendedForReconnect = true;
        selectL1HoldPending = false;
        mainThreadHandler.removeCallbacks(statsOverlayToggleRunnable);
        inputManager.unregisterInputDeviceListener(this);
        disableSensors();
        deviceVibrator.cancel();
    }

    public void resumeAfterReconnect() {
        if (stopped || !suspendedForReconnect) {
            return;
        }

        suspendedForReconnect = false;
        inputManager.registerInputDeviceListener(this, null);
        enableSensors();
    }

    public void stop() {
        if (stopped) {
            return;
        }

        // Stop new device contexts from being created or used
        stopped = true;

        // Cancel any pending stats overlay toggle
        selectL1HoldPending = false;
        mainThreadHandler.removeCallbacks(statsOverlayToggleRunnable);

        // A suspended handler is already unregistered. Final teardown must still destroy all
        // retained contexts, but must not unregister the same listener twice.
        if (!suspendedForReconnect) {
            inputManager.unregisterInputDeviceListener(this);
        }
        suspendedForReconnect = false;''')
replace_once(
    controller,
    '''    public void destroy() {
        if (!stopped) {
            stop();
        }''',
    '''    public void destroy() {
        if (!stopped) {
            stop();
        }''')
# Block normal Android input while suspended without marking the handler terminal.
replace_once(
    controller,
    '''        // Don't return a context if we're stopped
        if (stopped) {
            return null;''',
    '''        // Don't return a context if we're stopped or temporarily suspended for reconnect.
        if (stopped || suspendedForReconnect) {
            return null;''')
replace_once(
    controller,
    '''    public void reportControllerState(int controllerId, int buttonFlags,
                                      float leftStickX, float leftStickY,
                                      float rightStickX, float rightStickY,
                                      float leftTrigger, float rightTrigger) {
        GenericControllerContext context = usbDeviceContexts.get(controllerId);''',
    '''    public void reportControllerState(int controllerId, int buttonFlags,
                                      float leftStickX, float leftStickY,
                                      float rightStickX, float rightStickY,
                                      float leftTrigger, float rightTrigger) {
        if (stopped || suspendedForReconnect) {
            return;
        }
        GenericControllerContext context = usbDeviceContexts.get(controllerId);''')
replace_once(
    controller,
    '''    public void reportControllerMotion(int controllerId, byte motionType, float motionX, float motionY, float motionZ) {
        GenericControllerContext context = usbDeviceContexts.get(controllerId);''',
    '''    public void reportControllerMotion(int controllerId, byte motionType, float motionX, float motionY, float motionZ) {
        if (stopped || suspendedForReconnect) {
            return;
        }
        GenericControllerContext context = usbDeviceContexts.get(controllerId);''')

# Ensure touchpad events that bypass getContextForEvent() are also quiet while suspended.
replace_once(
    controller,
    '''    public boolean tryHandleTouchpadEvent(MotionEvent event) {
        // Bail if this is not a touchpad or mouse event''',
    '''    public boolean tryHandleTouchpadEvent(MotionEvent event) {
        if (stopped || suspendedForReconnect) {
            return false;
        }
        // Bail if this is not a touchpad or mouse event''')


game = 'app/src/main/java/com/limelight/Game.java'
replace_once(
    game,
    '''    private void stopConnection() {
        if (connecting || connected) {
            connecting = connected = false;
            updatePipAutoEnter();

            controllerHandler.stop();''',
    '''    private void stopConnection() {
        stopConnection(false);
    }

    private void stopConnection(boolean preserveControllerStateForReconnect) {
        if (connecting || connected) {
            connecting = connected = false;
            updatePipAutoEnter();

            if (preserveControllerStateForReconnect) {
                controllerHandler.suspendForReconnect();
            } else {
                controllerHandler.stop();
            }''')
replace_once(
    game,
    '''        stopConnection();
        releaseStreamingWifiLocks();''',
    '''        stopConnection(true);
        releaseStreamingWifiLocks();''')
replace_once(
    game,
    '''                connected = true;
                connecting = false;
                fastResumeLifecycleArmed = false;''',
    '''                connected = true;
                connecting = false;
                if (controllerHandler != null) {
                    controllerHandler.resumeAfterReconnect();
                }
                fastResumeLifecycleArmed = false;''')

# If a reconnect attempt fails asynchronously, don't leave an input handler permanently suspended.
replace_once(
    game,
    '''    private void handleConnectionTerminatedFinal(final int errorCode) {
        // Perform a connection test if the failure could be due to a blocked port''',
    '''    private void handleConnectionTerminatedFinal(final int errorCode) {
        if (controllerHandler != null) {
            controllerHandler.resumeAfterReconnect();
        }
        // Perform a connection test if the failure could be due to a blocked port''')

# Clear stale codec queue/input state when setup() creates a new codec session after prepareForStop().
renderer = 'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java'
replace_once(
    renderer,
    '''        // A fresh codec session must not inherit the old renderer's terminal stopping state.
        stopping = false;
        this.targetFps = (redrawRate > 0 ? redrawRate : 60);''',
    '''        // A fresh codec session must not inherit the old renderer's terminal stopping state
        // or buffer indices/timestamps that belonged to the released MediaCodec instance.
        stopping = false;
        nextInputBuffer = null;
        nextInputBufferIndex = -1;
        outputBufferQueue.clear();
        enqueueNsByPtsUs.clear();
        this.targetFps = (redrawRate > 0 ? redrawRate : 60);''')
