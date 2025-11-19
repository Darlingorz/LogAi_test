package com.logai.security.oauth2.controller;

import com.logai.common.exception.BusinessException;
import com.logai.security.oauth2.dto.AuthorizationRequest;
import com.logai.security.oauth2.dto.ClientRegistrationRequest;
import com.logai.security.oauth2.dto.OauthTokenResponse;
import com.logai.security.oauth2.entity.AuthorizationCode;
import com.logai.security.oauth2.entity.OAuth2Client;
import com.logai.security.oauth2.service.OAuth2AuthorizationService;
import com.logai.security.oauth2.service.OAuth2ClientService;
import com.logai.security.oauth2.service.OAuth2TokenService;
import com.logai.security.oauth2.support.ForwardedRequestUtil;
import com.logai.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/oauth2", "/mcp/api/oauth2"})
@RequiredArgsConstructor
public class OAuth2AuthorizationController {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2ClientService clientService;
    private final OAuth2TokenService tokenService;

    @Value("${security.oauth2.login-url:https://www.logai.chat/login}")
    private String oauthLoginPageUrl;

    /**
     * 授权端点 - 处理授权请求
     */
    @GetMapping("/authorize")
    public ResponseEntity<Object> authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, name = "code_challenge") String codeChallenge,
            @RequestParam(required = false, name = "code_challenge_method") String codeChallengeMethod,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        log.info("OAuth2授权请求 - client_id: {}, grant_type: {}", clientId, responseType);

        if (!StringUtils.hasText(redirectUri)) {
            throw BusinessException.OAuth2Exception("invalid_request", "redirect_uri不能为空");
        }

        if (!"code".equalsIgnoreCase(responseType)) {
            throw BusinessException.OAuth2Exception("unsupported_response_type", "仅支持授权码模式");
        }

        try {
            OAuth2Client client = clientService.validateClient(clientId, redirectUri);
            if (user == null) {
                // 需要用户登录
                URI loginRedirect = buildLoginRedirectUri(request);
                return ResponseEntity
                        .status(HttpStatus.FOUND)
                        .location(loginRedirect)
                        .build();
            }

            AuthorizationRequest authorizationRequest = AuthorizationRequest.builder()
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scope(scope != null ? scope : "read")
                    .state(state)
                    .codeChallenge(codeChallenge)
                    .codeChallengeMethod(codeChallengeMethod)
                    .responseType(responseType)
                    .userId(user.getId())
                    .userUuid(user.getUuid())
                    .build();

            AuthorizationCode authCode = authorizationService.createAuthorizationCode(authorizationRequest);

            UriComponentsBuilder redirectBuilder = UriComponentsBuilder
                    .fromUriString(redirectUri)
                    .queryParam("code", authCode.getCode());

            if (state != null) {
                redirectBuilder.queryParam("state", state);
            }

            URI redirectUrl = redirectBuilder
                    .build()
                    .encode()
                    .toUri();
            log.info("OAuth2授权成功 - client_id: {}, code: {}",
                    clientId, authCode.getCode());

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(redirectUrl)
                    .build();

        } catch (Exception error) {
            log.error("OAuth2授权失败: {}", error.getMessage());

            String errorCode = "invalid_request";

            String errorDescription = error.getMessage();
            if (error instanceof BusinessException oauth2Exception) {
                errorCode = oauth2Exception.getCode();
            }

            // 构建错误重定向URL
            UriComponentsBuilder errorBuilder = UriComponentsBuilder
                    .fromUriString(redirectUri)
                    .queryParam("error", errorCode);

            if (StringUtils.hasText(errorDescription)) {
                errorBuilder.queryParam("error_description", errorDescription);
            }

            if (state != null) {
                errorBuilder.queryParam("state", state);
            }

            URI errorUri = errorBuilder
                    .build()
                    .encode()
                    .toUri();

            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(errorUri)
                    .build();
        }
    }


    private ClientCredentials resolveClientCredentials(String clientId,
                                                       String clientSecret,
                                                       HttpServletRequest request) {
        if (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()) {
            return new ClientCredentials(clientId, clientSecret);
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Basic ")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(authorization.substring(6));
                String token = new String(decoded, StandardCharsets.UTF_8);
                int separator = token.indexOf(':');
                if (separator > 0) {
                    String id = token.substring(0, separator);
                    String secret = token.substring(separator + 1);
                    if (!id.isBlank() && !secret.isBlank()) {
                        return new ClientCredentials(id, secret);
                    }
                }
            } catch (IllegalArgumentException ex) {
                log.warn("Basic认证头解析失败: {}", ex.getMessage());
            }
        }

        return new ClientCredentials(clientId, clientSecret);
    }

    private record ClientCredentials(String clientId, String clientSecret) {
    }

    @GetMapping("/token")
    public ResponseEntity<String> probeTokenEndpoint() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body("OAuth2 token endpoint is alive.");
    }

    /**
     * 令牌端点 - 处理令牌请求
     */
    @PostMapping("/token")
    public ResponseEntity<OauthTokenResponse> token(
            HttpServletRequest request,
            @RequestParam MultiValueMap<String, String> formData) {

        String grantType = formData.getFirst("grant_type");
        String code = formData.getFirst("code");

        ClientCredentials credentials = resolveClientCredentials(
                formData.getFirst("client_id"),
                formData.getFirst("client_secret"),
                request
        );

        String clientId = credentials.clientId();
        String clientSecret = credentials.clientSecret();
        String redirectUri = formData.getFirst("redirect_uri");
        String refreshToken = formData.getFirst("refresh_token");
        String codeVerifier = formData.getFirst("code_verifier");

        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw BusinessException.OAuth2Exception("invalid_client", "客户端凭据缺失");
        }

        log.info("OAuth2令牌请求 - grant_type: {}, client_id: {}", grantType, clientId);

        try {
            // 同步校验客户端（假设已经从 Mono 改成同步方法）
            OAuth2Client client = clientService.authenticateClient(clientId, clientSecret);

            OauthTokenResponse tokenResponse;

            if ("authorization_code".equals(grantType)) {
                // 授权码模式
                tokenResponse = handleAuthorizationCodeGrant(code, redirectUri, codeVerifier, clientId);
            } else if ("refresh_token".equals(grantType)) {
                // 刷新令牌模式
                tokenResponse = handleRefreshTokenGrant(refreshToken, clientId);
            } else {
                throw BusinessException.OAuth2Exception("unsupported_grant_type", "不支持的授权类型");
            }

            return ResponseEntity.ok(tokenResponse);

        } catch (Exception e) {
            log.error("OAuth2令牌请求失败: {}", e.getMessage());

            OauthTokenResponse errorResponse = new OauthTokenResponse();
            errorResponse.setError("invalid_request");
            errorResponse.setErrorDescription(e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(errorResponse);
        }
    }


    /**
     * 客户端注册端点 - 注册新的OAuth2客户端
     * 需要管理员权限或者其他认证机制
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerClient(
            @RequestBody ClientRegistrationRequest request,
            ServerHttpRequest httpRequest) {

        log.info("OAuth2客户端注册请求 - client_name: {}", request.getClientName());
        try {
            OAuth2Client client = clientService.registerClient(request);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("client_id", client.getClientId());
            if (client.getPlainClientSecret() != null) {
                body.put("client_secret", client.getPlainClientSecret());
            }
            body.put("client_id_issued_at", Instant.now().getEpochSecond());
            body.put("client_secret_expires_at", 0);
            body.put("client_name", client.getClientName());
            body.put("redirect_uris", client.getRedirectUris());
            body.put("grant_types", client.getGrantTypes());
            body.put("response_types", client.getResponseTypes());
            body.put("scope", String.join(" ", client.getScope()));
            String context = resolveRequestContext(httpRequest);
            String baseUri = ForwardedRequestUtil.resolveBaseUri(httpRequest, context);
            body.put("registration_client_uri",
                    baseUri + "/api/oauth2/register/" + client.getClientId());

            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "invalid_client_metadata");
            err.put("error_description", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }


    private String resolveRequestContext(ServerHttpRequest request) {
        String path = request.getPath().value();
        if (path.startsWith("/mcp/")) {
            return "mcp";
        }
        return null;
    }


    private OauthTokenResponse handleAuthorizationCodeGrant(String code, String redirectUri,
                                                            String codeVerifier, String clientId) {
        if (code == null || code.isEmpty()) {
            throw BusinessException.OAuth2Exception("invalid_request", "授权码不能为空");
        }
        // 验证授权码
        AuthorizationCode authCode = authorizationService.validateAuthorizationCode(code, clientId, redirectUri, codeVerifier);

        OauthTokenResponse tokenResponse = tokenService.generateTokens(authCode);
        log.info("访问令牌生成成功 - 客户端: {}, 用户: {}",
                clientId, authCode.getUserId());
        return tokenResponse;
    }

    private OauthTokenResponse handleRefreshTokenGrant(String refreshToken, String clientId) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw BusinessException.OAuth2Exception("invalid_request", "刷新令牌不能为空");
        }
        // 使用简化的OAuth2TokenService刷新令牌
        return tokenService.refreshAccessToken(refreshToken, clientId);
    }

    private URI buildLoginRedirectUri(HttpServletRequest request) {
        // 只允许转发这些 OAuth2 标准参数
        List<String> allowedParams = List.of(
                "response_type",
                "client_id",
                "redirect_uri",
                "scope",
                "state",
                "code_challenge",
                "code_challenge_method"
        );

        // 获取原始 query 参数
        Map<String, String[]> rawParams = request.getParameterMap();
        MultiValueMap<String, String> safeParams = new LinkedMultiValueMap<>();

        // 逐个参数安全编码
        rawParams.forEach((key, values) -> {
            if (!allowedParams.contains(key)) {
                return;
            }

            for (String value : values) {
                if (value == null) {
                    continue;
                }

                // 使用 UriComponentsBuilder 的 encode 做安全编码
                String encoded = UriComponentsBuilder.newInstance()
                        .queryParam("v", value)
                        .build()
                        .encode()
                        .getQueryParams()
                        .getFirst("v");

                safeParams.add(key, encoded);
            }
        });

        // 构建安全登录跳转 URL
        return UriComponentsBuilder
                .fromUriString(oauthLoginPageUrl)
                .queryParams(safeParams)
                .build()
                .encode()
                .toUri();
    }
}