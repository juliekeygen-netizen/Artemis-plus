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

    private WifiManager wifiManager;
    private Handler handler;
    private Runnable pollRunnable;
    private WifiCallback callback;
    private boolean running;

    private int lastQuality = -1;

    public interface WifiCallback {
        void onWifiQualityChanged(int quality, int rssi, int linkSpeed);
    }

    public void start(Context context, WifiCallback callback) {
        if (running) {
            return;
        }

        this.callback = callback;
        this.running = true;

        try {
            wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        } catch (Exception e) {
            LimeLog.warning("Failed to get WifiManager: " + e.getMessage());
            return;
        }

        handler = new Handler(Looper.getMainLooper());

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }

                try {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    if (info != null) {
                        int rssi = info.getRssi();
                        int linkSpeed = info.getLinkSpeed();

                        int quality = calculateQuality(rssi, linkSpeed);

                        // Always report so the overlay stays updated
                        callback.onWifiQualityChanged(quality, rssi, linkSpeed);
                        lastQuality = quality;
                    }
                } catch (Exception e) {
                    LimeLog.warning("WiFi poll failed: " + e.getMessage());
                }

                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
            }
        };

        handler.post(pollRunnable);
    }

    public void stop() {
        running = false;
        if (handler != null) {
            handler.removeCallbacks(pollRunnable);
        }
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
