package com.logai.security.oauth2.service;

import com.logai.common.exception.BusinessException;
import com.logai.security.oauth2.dto.ClientRegistrationRequest;
import com.logai.security.oauth2.dto.ClientUpdateRequest;
import com.logai.security.oauth2.entity.OAuth2Client;
import com.logai.security.oauth2.repository.OAuth2ClientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientService {
    private final OAuth2ClientMapper clientRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 验证客户端
     */
    public OAuth2Client validateClient(String clientId, String redirectUri) {
        OAuth2Client client = clientRepository.findByClientId(clientId);
        if (client == null) {
            throw BusinessException.OAuth2Exception("invalid_client", "客户端不存在");
        }
        // 校验 redirectUri
        if (!matchesRedirectUri(client.getRedirectUris(), redirectUri)) {
            throw BusinessException.OAuth2Exception("invalid_grant", "redirect_uri 不匹配");
        }

        if (!client.isEnabled()) {
            throw BusinessException.OAuth2Exception("invalid_client", "客户端已禁用");
        }

        return client;
    }


    /**
     * 客户端认证
     */
    public OAuth2Client authenticateClient(String clientId, String clientSecret) {
        OAuth2Client client = clientRepository.findByClientId(clientId);
        if (client == null) {
            throw BusinessException.OAuth2Exception("invalid_client", "客户端不存在");
        }
        // 验证客户端密钥
        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw BusinessException.OAuth2Exception("invalid_client", "客户端密钥错误");
        }

        // 验证客户端状态
        if (!client.isEnabled()) {
            throw BusinessException.OAuth2Exception("invalid_client", "客户端已禁用");
        }

        // 更新最后使用时间
        client.setLastUsedAt(LocalDateTime.now());
        clientRepository.updateById(client);

        return clientRepository.selectById(client.getId());
    }

    /**
     * 注册客户端
     */
    public OAuth2Client registerClient(ClientRegistrationRequest request) {
        // 1. redirectUris 必填
        List<String> redirectUris = Optional.ofNullable(request.getRedirectUris())
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim).
                        distinct().toList())
                .orElseThrow(() -> BusinessException.OAuth2Exception("invalid_client_metadata", "redirect_uris 不能为空"));

        if (redirectUris.isEmpty()) {
            throw BusinessException.OAuth2Exception("invalid_client_metadata", "redirect_uris 不能为空");
        }

        // 3. 校验 URL 格式（避免传非法字符串）
        redirectUris.forEach(uri -> {
            try {
                new URI(uri);
            } catch (Exception e) {
                throw BusinessException.OAuth2Exception("invalid_redirect_uri", "非法 redirect_uri：" + uri);
            }
        });

        // 4. grantTypes 默认值
        List<String> grantTypes = Optional.ofNullable(request.getGrantTypes())
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList()
                )
                .orElse(List.of("authorization_code", "refresh_token"));

        // 5. scope：拆分或 List<String>
        List<String> scopes =
                Optional.ofNullable(request.getScope())
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.stream()
                                .filter(StringUtils::hasText)
                                .map(String::trim)
                                .distinct()
                                .toList()
                        )
                        .orElse(List.of("read", "offline_access"));


        List<String> responseTypes =
                Optional.ofNullable(request.getResponseTypes())
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.stream()
                                .filter(StringUtils::hasText)
                                .map(String::trim)
                                .distinct()
                                .toList()
                        )
                        .orElse(List.of("code"));

        // 生成客户端ID和密钥
        String clientId = generateClientId();
        String clientSecret = generateClientSecret();

        OAuth2Client client = new OAuth2Client();
        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(clientSecret));
        client.setPlainClientSecret(clientSecret);

        client.setClientName(request.getClientName());
        client.setEnabled(true);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        client.setLastUsedAt(LocalDateTime.now());

        client.setRedirectUris(redirectUris);
        client.setResponseTypes(responseTypes);
        client.setGrantTypes(grantTypes);
        client.setScope(scopes);

        client.setTokenEndpointAuthMethod(
                StringUtils.hasText(request.getTokenEndpointAuthMethod())
                        ? request.getTokenEndpointAuthMethod().trim()
                        : "client_secret_basic");

        client.setClientUri(request.getClientUri());
        client.setLogoUri(request.getLogoUri());
        if (clientRepository.insert(client) == 0) {
            throw BusinessException.OAuth2Exception("register_client_failed", "注册客户端失败");
        }
        log.info("OAuth2客户端注册成功: {}", clientId);
        return clientRepository.findByClientId(clientId);

    }

    /**
     * 获取客户端信息
     */
    public OAuth2Client getClient(String clientId) {
        return clientRepository.findByClientId(clientId);
    }

    /**
     * 更新客户端
     */
    public OAuth2Client updateClient(String clientId, ClientUpdateRequest request) {
        OAuth2Client client = clientRepository.findByClientId(clientId);
        if (StringUtils.hasText(request.getClientName())) {
            client.setClientName(request.getClientName());
        }

        if (StringUtils.hasText(request.getDescription())) {
            client.setDescription(request.getDescription());
        }

        if (request.getRedirectUris() != null) {
            client.setRedirectUris(
                    request.getRedirectUris().stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList()
            );
        }

        if (request.getGrantTypes() != null) {
            client.setGrantTypes(
                    request.getGrantTypes().stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList()
            );
        }

        if (request.getResponseTypes() != null) {
            client.setResponseTypes(
                    request.getResponseTypes().stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList()
            );
        }

        if (request.getScope() != null) {
            client.setResponseTypes(
                    request.getScope().stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .toList()
            );
        }

        if (request.getEnabled() != null) {
            client.setEnabled(request.getEnabled());
        }

        client.setUpdatedAt(LocalDateTime.now());

        clientRepository.updateById(client);

        log.info("客户端更新成功 {}", clientId);

        return client;

    }

    /**
     * 重置客户端密钥
     */
    public OAuth2Client resetClientSecret(String clientId) {
        OAuth2Client client = clientRepository.findByClientId(clientId);
        String newClientSecret = generateClientSecret();
        client.setClientSecret(passwordEncoder.encode(newClientSecret));
        client.setPlainClientSecret(newClientSecret);
        clientRepository.updateById(client);
        log.info("客户端密钥重置成功: {}", clientId);
        return client;
    }


    private String generateClientId() {
        return "logai_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateClientSecret() {
        return Base64.getUrlEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes())
                .replace("=", "")
                .substring(0, 32);
    }

    private boolean matchesRedirectUri(List<String> registered, String incoming) {
        if (!StringUtils.hasText(incoming) || registered == null || registered.isEmpty()) {
            return false;
        }

        return registered.stream()
                .filter(StringUtils::hasText)
                .anyMatch(uri -> uri.equals(incoming));
    }
}
