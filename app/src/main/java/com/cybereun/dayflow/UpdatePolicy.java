package com.cybereun.dayflow;

import java.net.URI;

/** Pure update selection rules; no network, credentials or planner access. */
public final class UpdatePolicy {
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
