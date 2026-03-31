package com.limelight.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Lightweight overlay that renders performance and network stats on top of the video surface.
 * Supports three display modes: OFF, COMPACT (single line at top), FULL (multi-line box in top-right).
 */
public class StatsOverlay extends View {

    // Stats from server (received via control channel)
    private int serverBitrate;
    private int serverFecPct;
    private int serverThermalState; // 0=cool, 1=warm, 2=hot

    // Stats from client (measured locally)
    private float decodeTimeMs;
    private float renderTimeMs;
    private float networkLatencyMs;
    private int fps;
    private String codec = "";

    // WiFi info (from WifiMonitor)
    private int wifiQuality; // 0-4
    private int wifiRssi;
    private int wifiLinkSpeed;

    // Display modes
    public enum Mode { OFF, COMPACT, FULL }
    private Mode mode = Mode.OFF;

    private final Paint bgPaint;
    private final Paint textPaint;
    private final Paint warnPaint;
    private final Paint critPaint;
    private final RectF bgRect;

    private static final float TEXT_SIZE_SP = 12f;
    private static final float COMPACT_TEXT_SIZE_SP = 10f;
    private static final float PADDING_DP = 8f;
    private static final float LINE_SPACING_DP = 2f;
    private static final float CORNER_RADIUS_DP = 4f;

    public StatsOverlay(Context context) {
        this(context, null);
    }

    public StatsOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatsOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = context.getResources().getDisplayMetrics().density;

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.argb(180, 0, 0, 0));
        bgPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TEXT_SIZE_SP * density);

        warnPaint = new Paint(textPaint);
        warnPaint.setColor(Color.YELLOW);

        critPaint = new Paint(textPaint);
        critPaint.setColor(Color.RED);

        bgRect = new RectF();
    }

    /**
     * Cycle through OFF -> FULL -> COMPACT -> OFF
     */
    public void toggle() {
        switch (mode) {
            case OFF:
                mode = Mode.FULL;
                break;
            case FULL:
                mode = Mode.COMPACT;
                break;
            case COMPACT:
                mode = Mode.OFF;
                break;
        }
        invalidate();
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == Mode.OFF) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float padding = PADDING_DP * density;
        float cornerRadius = CORNER_RADIUS_DP * density;

        if (mode == Mode.COMPACT) {
            drawCompact(canvas, density, padding, cornerRadius);
        } else {
            drawFull(canvas, density, padding, cornerRadius);
        }
    }

    private void drawCompact(Canvas canvas, float density, float padding, float cornerRadius) {
        textPaint.setTextSize(COMPACT_TEXT_SIZE_SP * density);
        float textHeight = textPaint.descent() - textPaint.ascent();

        String line = buildCompactLine();

        float textWidth = textPaint.measureText(line);
        float boxWidth = textWidth + padding * 2;
        float boxHeight = textHeight + padding * 2;

        // Centered at top
        float left = (getWidth() - boxWidth) / 2f;
        bgRect.set(left, 0, left + boxWidth, boxHeight);
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

        canvas.drawText(line, left + padding, padding - textPaint.ascent(), getTextPaintForQuality());
    }

    private void drawFull(Canvas canvas, float density, float padding, float cornerRadius) {
        textPaint.setTextSize(TEXT_SIZE_SP * density);
        float lineHeight = textPaint.descent() - textPaint.ascent() + LINE_SPACING_DP * density;

        String[] lines = buildFullLines();

        // Measure max width
        float maxWidth = 0;
        for (String line : lines) {
            float w = textPaint.measureText(line);
            if (w > maxWidth) maxWidth = w;
        }

        float boxWidth = maxWidth + padding * 2;
        float boxHeight = lineHeight * lines.length + padding * 2;

        // Top-right corner
        float left = getWidth() - boxWidth - padding;
        float top = padding;
        bgRect.set(left, top, left + boxWidth, top + boxHeight);
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

        float y = top + padding - textPaint.ascent();
        for (String line : lines) {
            Paint paint = textPaint;
            if (line.startsWith("[!]")) {
                paint = warnPaint;
                line = line.substring(3);
            } else if (line.startsWith("[X]")) {
                paint = critPaint;
                line = line.substring(3);
            }
            canvas.drawText(line, left + padding, y, paint);
            y += lineHeight;
        }
    }

    private String buildCompactLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(fps).append(" FPS");
        if (codec != null && !codec.isEmpty()) {
            sb.append(" | ").append(codec);
        }
        sb.append(" | ").append(String.format("%.1fms", decodeTimeMs));
        sb.append(" | WiFi ").append(getWifiQualityLabel());
        if (serverThermalState > 0) {
            sb.append(" | ").append(getThermalLabel());
        }
        return sb.toString();
    }

    private String[] buildFullLines() {
        String thermalPrefix = serverThermalState >= 2 ? "[X]" : serverThermalState == 1 ? "[!]" : "";
        String wifiPrefix = wifiQuality <= 1 ? (wifiQuality == 0 ? "[X]" : "[!]") : "";

        return new String[] {
                "FPS: " + fps + (codec != null && !codec.isEmpty() ? " (" + codec + ")" : ""),
                "Decode: " + String.format("%.1f ms", decodeTimeMs),
                "Render: " + String.format("%.1f ms", renderTimeMs),
                "Network: " + String.format("%.1f ms", networkLatencyMs),
                "Bitrate: " + (serverBitrate > 0 ? serverBitrate + " kbps" : "N/A"),
                "FEC: " + (serverBitrate > 0 ? serverFecPct + "%" : "N/A"),
                thermalPrefix + "Thermal: " + getThermalLabel(),
                wifiPrefix + "WiFi: " + getWifiQualityLabel() + " (" + wifiRssi + " dBm, " + wifiLinkSpeed + " Mbps)"
        };
    }

    private String getWifiQualityLabel() {
        switch (wifiQuality) {
            case 4: return "Excellent";
            case 3: return "Good";
            case 2: return "Fair";
            case 1: return "Poor";
            case 0: return "Critical";
            default: return "Unknown";
        }
    }

    private String getThermalLabel() {
        switch (serverThermalState) {
            case 0: return "Cool";
            case 1: return "Warm";
            case 2: return "Hot";
            default: return "Unknown";
        }
    }

    private Paint getTextPaintForQuality() {
        if (wifiQuality == 0 || serverThermalState >= 2) {
            return critPaint;
        }
        if (wifiQuality == 1 || serverThermalState == 1) {
            return warnPaint;
        }
        return textPaint;
    }

    /**
     * Update stats received from the server via control channel.
     */
    public void updateServerStats(int bitrate, int fecPct, int thermal) {
        this.serverBitrate = bitrate;
        this.serverFecPct = fecPct;
        this.serverThermalState = thermal;
        invalidate();
    }

    /**
     * Update client-measured stats.
     */
    public void updateClientStats(float decode, float render, float network, int fps, String codec) {
        this.decodeTimeMs = decode;
        this.renderTimeMs = render;
        this.networkLatencyMs = network;
        this.fps = fps;
        this.codec = codec;
        invalidate();
    }

    /**
     * Update WiFi stats from WifiMonitor.
     */
    public void updateWifiStats(int quality, int rssi, int linkSpeed) {
        this.wifiQuality = quality;
        this.wifiRssi = rssi;
        this.wifiLinkSpeed = linkSpeed;
        invalidate();
    }
}
