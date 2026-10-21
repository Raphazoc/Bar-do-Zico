package br.com.bardozico.android;

import java.net.URI;

/** HTTPS origins only: no credentials, paths, query parameters or fragments. */
final class ConnectionAddress {
    static boolean valid(String candidate) {
        if (candidate == null || candidate.length() > 200) return false;
        try {
            URI uri = URI.create(candidate);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getRawUserInfo() == null && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535))
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    static boolean placeholder(String candidate) {
        try { return "bar-api.example.azurecontainerapps.io".equalsIgnoreCase(URI.create(candidate).getHost()); }
        catch (Exception e) { return false; }
    }

    static boolean sameOrigin(String address, String target) {
        if (!valid(address)) return false;
        try {
            URI a = URI.create(address), b = URI.create(target);
            return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost())
                && b.getRawUserInfo() == null && port(a) == port(b);
        } catch (Exception e) { return false; }
    }

    /** Only a deliberate main-frame tap from the configured server opens native settings. */
    static boolean settingsNavigation(String address, String current, String target, boolean mainFrame, boolean gesture) {
        return mainFrame && gesture && "bardozico://settings".equals(target) && sameOrigin(address,current);
    }

    private static int port(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }
}
