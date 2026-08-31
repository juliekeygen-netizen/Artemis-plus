from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'{label} anchor not found in {path}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# 1) Pure background policy: keep-alive follows the same transition exclusions as Fast Resume.
policy = 'app/src/main/java/com/limelight/preferences/BackgroundStreamingPolicy.java'
replace_once(
    policy,
    '    public static boolean isFastResume(String mode) {\n        return MODE_FAST_RESUME.equals(mode);\n    }\n',
    '    public static boolean isFastResume(String mode) {\n        return MODE_FAST_RESUME.equals(mode);\n    }\n\n'
    '    public static boolean isKeepAlive(String mode) {\n        return MODE_KEEP_ALIVE.equals(mode);\n    }\n',
    'keep alive mode helper')
replace_once(
    policy,
    '    public static boolean shouldUseFastResume(String mode,\n                                               boolean finishing,\n                                               boolean changingConfigurations,\n                                               boolean inPictureInPicture,\n                                               boolean externalDisplay) {\n        return isFastResume(mode) &&\n                !finishing &&\n                !changingConfigurations &&\n                !inPictureInPicture &&\n                !externalDisplay;\n    }\n',
    '    public static boolean shouldUseFastResume(String mode,\n                                               boolean finishing,\n                                               boolean changingConfigurations,\n                                               boolean inPictureInPicture,\n                                               boolean externalDisplay) {\n        return isFastResume(mode) &&\n                !finishing &&\n                !changingConfigurations &&\n                !inPictureInPicture &&\n                !externalDisplay;\n    }\n\n'
    '    public static boolean shouldArmKeepAliveBeforeSurfaceLoss(String mode,\n                                                               boolean finishing,\n                                                               boolean changingConfigurations,\n                                                               boolean pipTransitionExpected,\n                                                               boolean externalDisplay,\n                                                               boolean multiWindow) {\n        return isKeepAlive(mode) &&\n                !finishing &&\n                !changingConfigurations &&\n                !pipTransitionExpected &&\n                !externalDisplay &&\n                !multiWindow;\n    }\n\n'
    '    public static boolean shouldUseKeepAlive(String mode,\n                                             boolean finishing,\n                                             boolean changingConfigurations,\n                                             boolean inPictureInPicture,\n                                             boolean externalDisplay) {\n        return isKeepAlive(mode) &&\n                !finishing &&\n                !changingConfigurations &&\n                !inPictureInPicture &&\n                !externalDisplay;\n    }\n',
    'keep alive policy methods')

# 2) MoonBridge: serialize video callbacks so suspending waits for any in-flight MediaCodec submit.
bridge = 'app/src/main/java/com/limelight/nvstream/jni/MoonBridge.java'
replace_once(
    bridge,
    '    private static AudioRenderer audioRenderer;\n    private static VideoDecoderRenderer videoRenderer;\n    private static NvConnectionListener connectionListener;\n',
    '    private static AudioRenderer audioRenderer;\n    private static VideoDecoderRenderer videoRenderer;\n    private static NvConnectionListener connectionListener;\n\n'
    '    // Keep Alive uses this monitor as a decoder quiescence barrier. Suspending takes the\n'
    '    // same lock as decode submission, so once suspendVideoSubmissions() returns there is no\n'
    '    // callback still inside MediaCodecDecoderRenderer.submitDecodeUnit().\n'
    '    private static final Object videoSubmissionLock = new Object();\n'
    '    private static boolean videoSubmissionSuspended;\n'
    '    private static boolean videoSubmissionNeedsIdr;\n'
    '    private static volatile boolean audioPlaybackSuspended;\n',
    'bridge gate fields')
replace_once(
    bridge,
    '    //todo 不显示画面\n    public static int bridgeDrSubmitDecodeUnit(byte[] decodeUnitData, int decodeUnitLength, int decodeUnitType,\n                                               int frameNumber, int frameType, char frameHostProcessingLatency,\n                                               long receiveTimeMs, long enqueueTimeMs) {\n        if (videoRenderer != null) {\n            return videoRenderer.submitDecodeUnit(decodeUnitData, decodeUnitLength,\n                    decodeUnitType, frameNumber, frameType, frameHostProcessingLatency, receiveTimeMs, enqueueTimeMs);\n        }\n        else {\n            return DR_OK;\n        }\n    }\n',
    '    public static void suspendVideoSubmissions() {\n'
    '        synchronized (videoSubmissionLock) {\n'
    '            videoSubmissionSuspended = true;\n'
    '            videoSubmissionNeedsIdr = false;\n'
    '        }\n'
    '    }\n\n'
    '    public static void resumeVideoSubmissionsAtNextIdr() {\n'
    '        synchronized (videoSubmissionLock) {\n'
    '            videoSubmissionSuspended = false;\n'
    '            videoSubmissionNeedsIdr = true;\n'
    '            requestIdrFrame();\n'
    '        }\n'
    '    }\n\n'
    '    public static void setAudioPlaybackSuspended(boolean suspended) {\n'
    '        audioPlaybackSuspended = suspended;\n'
    '    }\n\n'
    '    //todo 不显示画面\n    public static int bridgeDrSubmitDecodeUnit(byte[] decodeUnitData, int decodeUnitLength, int decodeUnitType,\n                                               int frameNumber, int frameType, char frameHostProcessingLatency,\n                                               long receiveTimeMs, long enqueueTimeMs) {\n        synchronized (videoSubmissionLock) {\n            if (videoSubmissionSuspended || videoRenderer == null) {\n                return DR_OK;\n            }\n\n'
    '            // After a codec recreation, ignore inter-frames until the requested IDR arrives.\n'
    '            if (videoSubmissionNeedsIdr) {\n'
    '                if (frameType != FRAME_TYPE_IDR) {\n'
    '                    return DR_OK;\n'
    '                }\n'
    '                videoSubmissionNeedsIdr = false;\n'
    '            }\n\n'
    '            return videoRenderer.submitDecodeUnit(decodeUnitData, decodeUnitLength,\n                    decodeUnitType, frameNumber, frameType, frameHostProcessingLatency, receiveTimeMs, enqueueTimeMs);\n        }\n    }\n',
    'bridge video submission')
replace_once(
    bridge,
    '    //静音 todo\n    public static void bridgeArPlaySample(short[] pcmData) {\n        if (audioRenderer != null) {\n            audioRenderer.playDecodedAudio(pcmData);\n        }\n    }\n',
    '    //静音 todo\n    public static void bridgeArPlaySample(short[] pcmData) {\n        if (!audioPlaybackSuspended && audioRenderer != null) {\n            audioRenderer.playDecodedAudio(pcmData);\n        }\n    }\n',
    'bridge audio gate')
replace_once(
    bridge,
    '    public static native void stopConnection();\n\n    public static native void interruptConnection();\n',
    '    public static native void stopConnection();\n\n    public static native void requestIdrFrame();\n\n    public static native void interruptConnection();\n',
    'bridge native IDR declaration')

# 3) JNI exposure for the public moonlight-common-c IDR API.
simplejni = 'app/src/main/jni/moonlight-core/simplejni.c'
replace_once(
    simplejni,
    'JNIEXPORT void JNICALL\nJava_com_limelight_nvstream_jni_MoonBridge_stopConnection(JNIEnv *env, jclass clazz) {\n    LiStopConnection();\n}\n\nJNIEXPORT void JNICALL\nJava_com_limelight_nvstream_jni_MoonBridge_interruptConnection',
    'JNIEXPORT void JNICALL\nJava_com_limelight_nvstream_jni_MoonBridge_stopConnection(JNIEnv *env, jclass clazz) {\n    LiStopConnection();\n}\n\nJNIEXPORT void JNICALL\nJava_com_limelight_nvstream_jni_MoonBridge_requestIdrFrame(JNIEnv *env, jclass clazz) {\n    LiRequestIdrFrame();\n}\n\nJNIEXPORT void JNICALL\nJava_com_limelight_nvstream_jni_MoonBridge_interruptConnection',
    'simplejni IDR bridge')

# 4) MediaCodec renderer: reversible release/recreate around Activity Surface loss.
renderer = 'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java'
replace_once(
    renderer,
    '    @Override\n    public void cleanup() {\n        videoDecoder.release();\n    }\n',
    '    /**\n'
    '     * Releases Android decode/render resources without stopping the Moonlight transport.\n'
    '     * MoonBridge.suspendVideoSubmissions() must be called first so no decode callback is\n'
    '     * still using the MediaCodec instance while it is released here.\n'
    '     */\n'
    '    public synchronized void suspendForKeepAlive() {\n'
    '        stop();\n'
    '        if (videoDecoder != null) {\n'
    '            videoDecoder.release();\n'
    '            videoDecoder = null;\n'
    '        }\n'
    '        nextInputBuffer = null;\n'
    '        nextInputBufferIndex = -1;\n'
    '        outputBufferQueue.clear();\n'
    '        enqueueNsByPtsUs.clear();\n'
    '        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE);\n'
    '        codecRecoveryThreadQuiescedFlags = 0;\n'
    '        codecRecoveryAttempts = 0;\n'
    '        rendererThread = null;\n'
    '        choreographerHandlerThread = null;\n'
    '        choreographerHandler = null;\n'
    '    }\n\n'
    '    /** Recreates MediaCodec against a newly-created Activity Surface. */\n'
    '    public synchronized int resumeFromKeepAlive(Surface surface) {\n'
    '        if (surface == null || !surface.isValid()) {\n'
    '            return -1;\n'
    '        }\n'
    '        setRenderTarget(surface);\n'
    '        stopping = false;\n'
    '        nextInputBuffer = null;\n'
    '        nextInputBufferIndex = -1;\n'
    '        outputBufferQueue.clear();\n'
    '        enqueueNsByPtsUs.clear();\n'
    '        codecRecoveryType.set(CR_RECOVERY_TYPE_NONE);\n'
    '        codecRecoveryThreadQuiescedFlags = 0;\n'
    '        codecRecoveryAttempts = 0;\n'
    '        int result = initializeDecoder(false);\n'
    '        if (result == 0) {\n'
    '            start();\n'
    '        }\n'
    '        return result;\n'
    '    }\n\n'
    '    @Override\n    public synchronized void cleanup() {\n        if (videoDecoder != null) {\n            videoDecoder.release();\n            videoDecoder = null;\n        }\n    }\n',
    'renderer reversible lifecycle')

# 5) Foreground service. It protects process/session priority; Game still owns NvConnection in POC A.
service = Path('app/src/main/java/com/limelight/StreamKeepAliveService.java')
service.write_text('''package com.limelight;\n\nimport android.app.Notification;\nimport android.app.NotificationChannel;\nimport android.app.NotificationManager;\nimport android.app.Service;\nimport android.content.Context;\nimport android.content.Intent;\nimport android.content.pm.ServiceInfo;\nimport android.os.Build;\nimport android.os.IBinder;\n\nimport androidx.annotation.Nullable;\nimport androidx.core.content.ContextCompat;\n\n/**\n * Foreground-process anchor for experimental true connection keep-alive. NvConnection ownership\n * remains with Game in the first POC; this service exists so Android does not immediately treat\n * the live network session as ordinary background work.\n */\npublic class StreamKeepAliveService extends Service {\n    private static final String CHANNEL_ID = "stream_keep_alive";\n    private static final int NOTIFICATION_ID = 0x41524B41;\n\n    public static void start(Context context) {\n        ContextCompat.startForegroundService(context, new Intent(context, StreamKeepAliveService.class));\n    }\n\n    public static void stop(Context context) {\n        context.stopService(new Intent(context, StreamKeepAliveService.class));\n    }\n\n    @Override\n    public void onCreate() {\n        super.onCreate();\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n            NotificationChannel channel = new NotificationChannel(\n                    CHANNEL_ID, "Background streaming", NotificationManager.IMPORTANCE_LOW);\n            channel.setDescription("Keeps an active Artemis streaming connection alive");\n            getSystemService(NotificationManager.class).createNotificationChannel(channel);\n        }\n    }\n\n    @Override\n    public int onStartCommand(Intent intent, int flags, int startId) {\n        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O\n                ? new Notification.Builder(this, CHANNEL_ID)\n                : new Notification.Builder(this);\n        Notification notification = builder\n                .setSmallIcon(R.drawable.app_icon)\n                .setContentTitle("Artemis Plus background streaming")\n                .setContentText("Keeping the streaming connection alive")\n                .setOngoing(true)\n                .setCategory(Notification.CATEGORY_SERVICE)\n                .build();\n\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n            startForeground(NOTIFICATION_ID, notification,\n                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);\n        } else {\n            startForeground(NOTIFICATION_ID, notification);\n        }\n        return START_NOT_STICKY;\n    }\n\n    @Override\n    public void onDestroy() {\n        stopForeground(true);\n        super.onDestroy();\n    }\n\n    @Nullable\n    @Override\n    public IBinder onBind(Intent intent) {\n        return null;\n    }\n}\n''', encoding='utf-8')

# 6) Manifest permissions/service declaration for connectedDevice FGS on targetSdk 34.
manifest = 'app/src/main/AndroidManifest.xml'
replace_once(
    manifest,
    '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n',
    '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n'
    '    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />\n',
    'foreground service permissions')
replace_once(
    manifest,
    '        <service\n            android:name=".binding.input.driver.UsbDriverService"\n            android:label="USB Driver Service" />\n',
    '        <service\n            android:name=".binding.input.driver.UsbDriverService"\n            android:label="USB Driver Service" />\n'
    '        <service\n'
    '            android:name=".StreamKeepAliveService"\n'
    '            android:exported="false"\n'
    '            android:foregroundServiceType="connectedDevice" />\n',
    'keep alive service manifest')

# 7) Expand pure policy tests. Keep Alive remains hidden from preference resources in this slice.
test = 'app/src/test/java/com/limelight/preferences/BackgroundStreamingPolicyTest.java'
replace_once(
    test,
    '    @Test\n    public void fastResumeOnlyOwnsOrdinaryBackgroundStop() {',
    '    @Test\n'
    '    public void keepAliveUsesOnlyOrdinaryBackgroundTransitions() {\n'
    '        assertTrue(BackgroundStreamingPolicy.isKeepAlive(BackgroundStreamingPolicy.MODE_KEEP_ALIVE));\n'
    '        assertFalse(BackgroundStreamingPolicy.isKeepAlive(BackgroundStreamingPolicy.MODE_FAST_RESUME));\n'
    '        assertTrue(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(\n'
    '                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false, false, false, false, false));\n'
    '        assertFalse(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(\n'
    '                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false, false, true, false, false));\n'
    '        assertFalse(BackgroundStreamingPolicy.shouldArmKeepAliveBeforeSurfaceLoss(\n'
    '                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false, false, false, false, true));\n'
    '        assertTrue(BackgroundStreamingPolicy.shouldUseKeepAlive(\n'
    '                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false, false, false, false));\n'
    '        assertFalse(BackgroundStreamingPolicy.shouldUseKeepAlive(\n'
    '                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, true, false, false, false));\n'
    '        assertFalse(BackgroundStreamingPolicy.shouldUseKeepAlive(\n'
    '                BackgroundStreamingPolicy.MODE_KEEP_ALIVE, false, false, true, false));\n'
    '    }\n\n'
    '    @Test\n    public void fastResumeOnlyOwnsOrdinaryBackgroundStop() {',
    'keep alive policy test')
