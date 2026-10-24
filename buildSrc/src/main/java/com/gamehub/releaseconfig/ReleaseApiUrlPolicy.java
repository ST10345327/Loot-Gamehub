package com.gamehub.releaseconfig;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class ReleaseApiUrlPolicy {
    private ReleaseApiUrlPolicy() {}

    public static void validate(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Set RELEASE_API_BASE_URL to the hosted HTTPS API before building a release.");
        }
        if (!endpoint.endsWith("/")) {
            throw new IllegalArgumentException("The API base URL must end with '/'.");
        }

        final URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("RELEASE_API_BASE_URL must be a valid absolute HTTPS URL.", error);
        }

        String host = uri.getHost();
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("RELEASE_API_BASE_URL must include an absolute URL hostname.");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("The API base URL must not contain credentials, a query or a fragment.");
        }
        if (host == null) {
            String authority = uri.getRawAuthority();
            if (authority != null && authority.matches("[0-9.]+")) {
                Long legacyIpv4 = parseLegacyIpv4(authority);
                if (legacyIpv4 != null && isLocalOnlyIpv4(legacyIpv4)) {
                    throw new IllegalArgumentException("Release builds must not use a local-only API base URL.");
                }
            }
            throw new IllegalArgumentException("RELEASE_API_BASE_URL must include an absolute URL hostname.");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65535 || (port == -1 && uri.getRawAuthority().endsWith(":"))) {
            throw new IllegalArgumentException("The API base URL port must be between 1 and 65535.");
        }
        if (!"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Release builds require an HTTPS API base URL.");
        }
        if (isLocalOnlyHost(host)) {
            throw new IllegalArgumentException("Release builds must not use a local-only API base URL.");
        }
    }

    private static boolean isLocalOnlyHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.equals("localhost.localdomain") || host.endsWith(".localhost.localdomain")
                || host.equals("ip6-localhost") || host.equals("ip6-loopback")
                || host.endsWith(".local") || host.endsWith(".lan")
                || host.endsWith(".internal") || host.endsWith(".home.arpa")
                || host.endsWith(".intranet")
                || (host.indexOf('.') < 0 && host.indexOf(':') < 0)) {
            return true;
        }

        Long ipv4 = parseLegacyIpv4(host);
        if (ipv4 != null) {
            return isLocalOnlyIpv4(ipv4);
        }
        if (host.indexOf(':') < 0) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            if (address instanceof Inet4Address) {
                return isLocalOnlyIpv4(toUnsignedLong(bytes));
            }
            if (address instanceof Inet6Address && bytes.length == 16) {
                boolean uniqueLocal = (bytes[0] & 0xfe) == 0xfc;
                boolean mappedIpv4 = allZero(bytes, 0, 10)
                        && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
                boolean compatibleIpv4 = allZero(bytes, 0, 12);
                return uniqueLocal || ((mappedIpv4 || compatibleIpv4)
                        && isLocalOnlyIpv4(toUnsignedLong(bytes, 12)));
            }
            return false;
        } catch (Exception error) {
            throw new IllegalArgumentException("The API base URL contains an invalid IP address.", error);
        }
    }

    private static Long parseLegacyIpv4(String host) {
        boolean numericDotted = host.matches("[0-9.]+");
        boolean hexadecimal = host.contains("0x") && host.matches("[0-9a-fx.]+");
        if (!numericDotted && !hexadecimal) {
            return null;
        }

        String[] parts = host.split("\\.", -1);
        if (parts.length == 0 || parts.length > 4) {
            throw new IllegalArgumentException("The API base URL contains an invalid IPv4 address.");
        }
        long[] values = new long[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                throw new IllegalArgumentException("The API base URL contains an invalid IPv4 address.");
            }
            int radix = 10;
            String digits = part;
            if (part.startsWith("0x")) {
                radix = 16;
                digits = part.substring(2);
            } else if (part.length() > 1 && part.startsWith("0")) {
                radix = 8;
                digits = part.substring(1);
            }
            try {
                values[index] = digits.isEmpty() ? 0 : Long.parseLong(digits, radix);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("The API base URL contains an invalid IPv4 address.", error);
            }
        }

        for (int index = 0; index < values.length - 1; index++) {
            if (values[index] > 255) {
                throw new IllegalArgumentException("The API base URL contains an invalid IPv4 address.");
            }
        }
        int lastBits = 8 * (5 - values.length);
        if (values[values.length - 1] >= (1L << lastBits)) {
            throw new IllegalArgumentException("The API base URL contains an invalid IPv4 address.");
        }
        long result = 0;
        for (int index = 0; index < values.length - 1; index++) {
            result = (result << 8) | values[index];
        }
        return (result << lastBits) | values[values.length - 1];
    }

    private static boolean isLocalOnlyIpv4(long address) {
        int first = (int) (address >>> 24);
        int second = (int) ((address >>> 16) & 0xff);
        return first == 0 || first == 10 || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 100 && second >= 64 && second <= 127);
    }

    private static long toUnsignedLong(byte[] bytes) {
        return toUnsignedLong(bytes, 0);
    }

    private static long toUnsignedLong(byte[] bytes, int offset) {
        long result = 0;
        for (int index = offset; index < offset + 4; index++) {
            result = (result << 8) | (bytes[index] & 0xffL);
        }
        return result;
    }

    private static boolean allZero(byte[] bytes, int start, int end) {
        for (int index = start; index < end; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return true;
    }
}
