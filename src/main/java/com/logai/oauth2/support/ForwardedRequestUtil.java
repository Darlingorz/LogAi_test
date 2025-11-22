package com.logai.oauth2.support;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;

public final class ForwardedRequestUtil {

    private ForwardedRequestUtil() {
    }

    public static String resolveBaseUri(ServerHttpRequest request, String context) {
        URI uri = request.getURI();
        String scheme = resolveScheme(request, uri.getScheme());
        String authority = resolveAuthority(request, scheme, uri);

        StringBuilder base = new StringBuilder();
        base.append(scheme).append("://").append(authority);
        if (StringUtils.hasText(context)) {
            base.append('/').append(context);
        }
        return base.toString();
    }

    private static String resolveScheme(ServerHttpRequest request, String fallback) {
        String scheme = parseForwardedParameter(request.getHeaders().get("FORWARDED"), "proto");
        if (!StringUtils.hasText(scheme)) {
            scheme = firstHeaderValue(request, "X-Forwarded-Proto");
        }
        if (!StringUtils.hasText(scheme) && StringUtils.hasText(fallback)) {
            scheme = fallback;
        }
        if (!StringUtils.hasText(scheme)) {
            scheme = "https";
        }
        return scheme;
    }

    private static String resolveAuthority(ServerHttpRequest request, String scheme, URI uri) {
        String authority = parseForwardedParameter(request.getHeaders().get("FORWARDED"), "host");
        if (!StringUtils.hasText(authority)) {
            authority = firstHeaderValue(request, "X-Forwarded-Host");
        }
        if (!StringUtils.hasText(authority) && request.getHeaders().getHost() != null) {
            authority = request.getHeaders().getHost().toString();
        }
        if (!StringUtils.hasText(authority) && StringUtils.hasText(uri.getAuthority())) {
            authority = uri.getAuthority();
        }
        if (!StringUtils.hasText(authority) && StringUtils.hasText(uri.getHost())) {
            authority = uri.getHost();
            if (uri.getPort() > 0) {
                authority = authority + ":" + uri.getPort();
            }
        }
        if (!StringUtils.hasText(authority)) {
            authority = "localhost";
        }

        authority = authority.split(",")[0].trim();

        int pathSeparator = authority.indexOf('/');
        if (pathSeparator >= 0) {
            authority = authority.substring(0, pathSeparator).trim();
        }

        if (!authority.contains(":")) {
            String forwardedPort = firstHeaderValue(request, "X-Forwarded-Port");
            if (StringUtils.hasText(forwardedPort) && portRequired(scheme, forwardedPort)) {
                authority = authority + ":" + forwardedPort.trim();
            } else if (uri.getPort() > 0 && portRequired(scheme, String.valueOf(uri.getPort()))) {
                authority = authority + ":" + uri.getPort();
            }
        }

        return authority;
    }

    private static String firstHeaderValue(ServerHttpRequest request, String headerName) {
        String value = request.getHeaders().getFirst(headerName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private static boolean portRequired(String scheme, String portValue) {
        try {
            int port = Integer.parseInt(portValue.trim());
            if (port <= 0) {
                return false;
            }
            return switch (scheme.toLowerCase()) {
                case "http" -> port != 80;
                case "https" -> port != 443;
                default -> true;
            };
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String parseForwardedParameter(List<String> forwardedHeaders, String parameter) {
        if (forwardedHeaders == null || forwardedHeaders.isEmpty()) {
            return null;
        }
        for (String header : forwardedHeaders) {
            if (!StringUtils.hasText(header)) {
                continue;
            }
            String[] segments = header.split(",");
            for (String segment : segments) {
                String[] parts = segment.trim().split(";");
                for (String part : parts) {
                    String trimmed = part.trim();
                    int equals = trimmed.indexOf('=');
                    if (equals <= 0) {
                        continue;
                    }
                    String name = trimmed.substring(0, equals).trim();
                    if (!parameter.equalsIgnoreCase(name)) {
                        continue;
                    }
                    String value = trimmed.substring(equals + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
