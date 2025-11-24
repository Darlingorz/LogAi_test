package com.logai.mcp.controller;

import com.logai.oauth2.support.ForwardedRequestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class WellKnownController {

    @Value("${security.oauth2.issuer:}")
    private String configuredIssuer;

    @GetMapping({
            "/.well-known/openid-configuration",
            "/.well-known/openid-configuration/{context}",
            "/.well-known/openid-configuration/**",
            "/mcp/.well-known/openid-configuration"
    })
    public Mono<Map<String, Object>> openidConfiguration(ServerHttpRequest request,
                                                         @PathVariable(name = "context", required = false) String context) {
        String resolvedContext = resolveContext(request, context, "openid-configuration");
        String baseUri = ForwardedRequestUtil.resolveBaseUri(request, resolvedContext);
        String issuer = resolveIssuer(baseUri);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", issuer);
        body.put("authorization_endpoint", baseUri + "/api/oauth2/authorize");
        body.put("token_endpoint", baseUri + "/api/oauth2/token");
        body.put("registration_endpoint", baseUri + "/api/oauth2/register");
        body.put("scopes_supported", List.of("read", "offline_access"));
        body.put("response_types_supported", List.of("code"));
        body.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        body.put("code_challenge_methods_supported", List.of("S256"));
        body.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));

        return Mono.just(body);
    }

    @GetMapping({
            "/.well-known/oauth-authorization-server",
            "/.well-known/oauth-authorization-server/{context}",
            "/.well-known/oauth-authorization-server/**",
            "/mcp/.well-known/oauth-authorization-server"
    })
    public Mono<Map<String, Object>> oauthAuthorizationMetadata(ServerHttpRequest request,
                                                                @PathVariable(name = "context", required = false) String context) {
        String resolvedContext = resolveContext(request, context, "oauth-authorization-server");
        String baseUri = ForwardedRequestUtil.resolveBaseUri(request, resolvedContext);
        String issuer = resolveIssuer(baseUri);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", issuer);
        body.put("authorization_endpoint", baseUri + "/api/oauth2/authorize");
        body.put("token_endpoint", baseUri + "/api/oauth2/token");
        body.put("jwks_uri", baseUri + "/api/oauth2/jwks");
        body.put("registration_endpoint", baseUri + "/api/oauth2/register");

        return Mono.just(body);
    }

    @GetMapping({
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/{context}",
            "/.well-known/oauth-protected-resource/**",
            "/mcp/.well-known/oauth-protected-resource"
    })
    public Mono<Map<String, Object>> oauthProtectedResourceMetadata(ServerHttpRequest request,
                                                                    @PathVariable(name = "context", required = false) String context) {
        String resolvedContext = resolveContext(request, context, "oauth-protected-resource");
        String baseUri = ForwardedRequestUtil.resolveBaseUri(request, resolvedContext);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", resolveIssuer(baseUri));
        body.put("resource", baseUri + "/api");
        body.put("token_endpoint", baseUri + "/api/oauth2/token");
        body.put("scopes_supported", List.of("read", "offline_access"));

        return Mono.just(body);
    }

    private String resolveIssuer(String baseUri) {
        if (StringUtils.hasText(configuredIssuer)) {
            return configuredIssuer;
        }
        return baseUri;
    }

    private String resolveContext(ServerHttpRequest request, String contextFromPath, String discoverySegment) {
        if (StringUtils.hasText(contextFromPath)) {
            return contextFromPath;
        }

        String path = request.getPath().value();
        if (path.startsWith("/mcp/")) {
            return "mcp";
        }
        String suffixContext = extractSuffixContext(path, discoverySegment);
        if (suffixContext != null) {
            return suffixContext;
        }
        return null;
    }

    private String extractSuffixContext(String path, String discoverySegment) {
        String marker = "/.well-known/" + discoverySegment;
        int markerIndex = path.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }

        int suffixStart = markerIndex + marker.length();
        if (suffixStart >= path.length()) {
            return null;
        }

        if (path.charAt(suffixStart) != '/') {
            return null;
        }

        int contextStart = suffixStart + 1;
        if (contextStart >= path.length()) {
            return null;
        }

        int nextSlash = path.indexOf('/', contextStart);
        String contextSegment = nextSlash == -1 ? path.substring(contextStart) : path.substring(contextStart, nextSlash);
        return StringUtils.hasText(contextSegment) ? contextSegment : null;
    }

}
