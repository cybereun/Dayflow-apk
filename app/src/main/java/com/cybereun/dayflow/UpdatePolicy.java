package com.cybereun.dayflow;

import java.net.URI;

/** Pure update selection rules; no network, credentials or planner access. */
public final class UpdatePolicy {
    private static final String OFFICIAL_SIGNER = "83b1ef579ae005e90a10d247a984d1ceb83fdb81c3ff99ba9fc5b8c7f52abe0b";
    private static final String LEGACY_DEBUG_SIGNER = "89dbc4d6576a4bd5818e3ee9eefa499f962419621eb3a3ed8608f9961f844e7e";
    private static long[] version(String text) {
        if (text == null || !text.matches("v?\\d{1,8}\\.\\d{1,8}\\.\\d{1,8}")) return null;
        String[] parts = text.replaceFirst("^v", "").split("\\.");
        return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    }
    public static boolean isNewer(String candidate, String installed) {
        long[] next = version(candidate), current = version(installed);
        if (next == null || current == null) return false;
        for (int i = 0; i < 3; i++) if (next[i] != current[i]) return next[i] > current[i];
        return false;
    }
    public static String assetName(String version, String signerSha256) {
        if (version(version) == null || signerSha256 == null) return null;
        if (OFFICIAL_SIGNER.equalsIgnoreCase(signerSha256)) return "Dayflow-" + version.replaceFirst("^v", "") + ".apk";
        if (LEGACY_DEBUG_SIGNER.equalsIgnoreCase(signerSha256)) return "Dayflow-" + version.replaceFirst("^v", "") + "-debug-compat.apk";
        return null;
    }
    public static boolean allowedAsset(String value) {
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null
                && uri.getFragment() == null && value.equals(uri.normalize().toString())
                && uri.getRawPath().matches("/cybereun/Dayflow-apk/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk");
        } catch (Exception ignored) { return false; }
    }
    private UpdatePolicy() {}
}
