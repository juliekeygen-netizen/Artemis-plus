package com.limelight.wifi;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;

/**
 * Periodically polls WiFi signal quality and link speed, reporting changes
 * to a callback for display in the stats overlay and transmission to the server.
 */
public class WifiMonitor {

    private static final long POLL_INTERVAL_MS = 500;

    public static final int QUALITY_CRITICAL = 0;
    public static final int QUALITY_POOR = 1;
    public static final int QUALITY_FAIR = 2;
    public static final int QUALITY_GOOD = 3;
    public static final int QUALITY_EXCELLENT = 4;

    private volatile WifiManager wifiManager;
    private volatile Handler handler;
    private volatile Runnable pollRunnable;
    private volatile WifiCallback callback;
    private volatile boolean running;

    private int lastQuality = -1;

    public interface WifiCallback {
        void onWifiQualityChanged(int quality, int rssi, int linkSpeed);
    }

    public synchronized void start(Context context, WifiCallback callback) {
        if (running) {
            return;
        }
        if (context == null || callback == null) {
            LimeLog.warning("Unable to start WiFi monitor without context and callback");
            return;
        }

        final WifiManager resolvedWifiManager;
        try {
            resolvedWifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
        } catch (Exception e) {
            LimeLog.warning("Failed to get WifiManager: " + e.getMessage());
            return;
        }
        if (resolvedWifiManager == null) {
            LimeLog.warning("Failed to get WifiManager: service unavailable");
            return;
        }

        wifiManager = resolvedWifiManager;
        this.callback = callback;
        handler = new Handler(Looper.getMainLooper());

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }

                try {
                    WifiManager manager = wifiManager;
                    WifiCallback activeCallback = WifiMonitor.this.callback;
                    if (manager != null && activeCallback != null) {
                        WifiInfo info = manager.getConnectionInfo();
                        if (info != null) {
                            int rssi = info.getRssi();
                            int linkSpeed = info.getLinkSpeed();

                            int quality = calculateQuality(rssi, linkSpeed);

                            // Always report so the overlay stays updated
                            activeCallback.onWifiQualityChanged(quality, rssi, linkSpeed);
                            lastQuality = quality;
                        }
                    }
                } catch (Exception e) {
                    LimeLog.warning("WiFi poll failed: " + e.getMessage());
                }

                Handler activeHandler = handler;
                if (running && activeHandler != null) {
                    activeHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };

        // Mark the lifecycle active only after all resources required by the poller exist. This
        // keeps a failed service lookup restartable instead of permanently wedging start().
        running = true;
        handler.post(pollRunnable);
    }

    public synchronized void stop() {
        running = false;
        Handler activeHandler = handler;
        if (activeHandler != null) {
            activeHandler.removeCallbacksAndMessages(null);
        }
        pollRunnable = null;
        handler = null;
        callback = null;
        wifiManager = null;
        lastQuality = -1;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Calculate WiFi quality level based on RSSI and link speed.
     */
    private static int calculateQuality(int rssi, int linkSpeed) {
        if (rssi > -50 && linkSpeed > 400) {
            return QUALITY_EXCELLENT;
        } else if (rssi > -60 && linkSpeed > 200) {
            return QUALITY_GOOD;
        } else if (rssi > -70 && linkSpeed > 100) {
            return QUALITY_FAIR;
        } else if (rssi > -75) {
            return QUALITY_POOR;
        } else {
            return QUALITY_CRITICAL;
        }
    }

    /**
     * Build the 4-byte WiFi quality payload to send to the server.
     * Format:
     *   [0] quality (0-4)
     *   [1] rssi (signed byte)
     *   [2-3] link_speed (uint16 little-endian)
     */
    public static byte[] buildWifiQualityPayload(int quality, int rssi, int linkSpeed) {
        byte[] payload = new byte[4];
        payload[0] = (byte) quality;
        payload[1] = (byte) rssi;
        payload[2] = (byte) (linkSpeed & 0xFF);
        payload[3] = (byte) ((linkSpeed >> 8) & 0xFF);
        return payload;
    }
}
