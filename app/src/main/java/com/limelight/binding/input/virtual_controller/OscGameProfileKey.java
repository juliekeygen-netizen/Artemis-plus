package com.limelight.binding.input.virtual_controller;

/** Builds a stable, host-scoped identity for automatic per-game OSC profile selection. */
public final class OscGameProfileKey {
    private static final String VERSION_PREFIX = "v1";

    private OscGameProfileKey() {
    }

    public static String build(String pcUuid, String host, String appUuid, int appId) {
        String pcIdentity = firstNonBlank(pcUuid, host);
        if (pcIdentity == null) {
            return null;
        }

        String normalizedAppUuid = normalize(appUuid);
        String appIdentity;
        if (normalizedAppUuid != null) {
            appIdentity = "uuid=" + escape(normalizedAppUuid);
        } else if (appId >= 0) {
            appIdentity = "id=" + appId;
        } else {
            return null;
        }

        return VERSION_PREFIX + "|pc=" + escape(pcIdentity) + "|app=" + appIdentity;
    }

    private static String firstNonBlank(String primary, String fallback) {
        String normalized = normalize(primary);
        return normalized != null ? normalized : normalize(fallback);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escape(String value) {
        return value
                .replace("%", "%25")
                .replace("|", "%7C")
                .replace("=", "%3D");
    }
}
