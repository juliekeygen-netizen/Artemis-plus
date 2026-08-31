package com.limelight.binding.video;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.limelight.LimeLog;

/**
 * A continuously drained MediaCodec output Surface used while a Keep Connection Alive stream
 * has no visible Activity Surface. Keeping the consumer drained prevents decoder back-pressure.
 */
@TargetApi(Build.VERSION_CODES.M)
public final class HeadlessVideoSurface implements AutoCloseable {
    private final HandlerThread drainThread;
    private final ImageReader imageReader;
    private volatile boolean closed;

    private HeadlessVideoSurface(int width, int height) {
        drainThread = new HandlerThread("Artemis Headless Video Drain");
        drainThread.start();

        imageReader = ImageReader.newInstance(
                Math.max(1, width),
                Math.max(1, height),
                ImageFormat.PRIVATE,
                2);
        Handler drainHandler = new Handler(drainThread.getLooper());
        imageReader.setOnImageAvailableListener(reader -> {
            if (closed) {
                return;
            }

            Image image = null;
            try {
                image = reader.acquireLatestImage();
            } catch (IllegalStateException | UnsupportedOperationException e) {
                LimeLog.warning("Headless video drain failed: " + e.getMessage());
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, drainHandler);
    }

    public static HeadlessVideoSurface create(int width, int height) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        try {
            return new HeadlessVideoSurface(width, height);
        } catch (RuntimeException e) {
            LimeLog.warning("Unable to create headless video Surface: " + e.getMessage());
            return null;
        }
    }

    public Surface getSurface() {
        return imageReader.getSurface();
    }

    public boolean isValid() {
        Surface surface = getSurface();
        return !closed && surface != null && surface.isValid();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            imageReader.setOnImageAvailableListener(null, null);
        } catch (RuntimeException ignored) {
        }
        imageReader.close();
        drainThread.quitSafely();
    }
}
