package com.logai.oauth2.service;

import com.logai.common.exception.BusinessException;
import com.logai.oauth2.dto.AuthorizationRequest;
import com.logai.oauth2.entity.AuthorizationCode;
import com.logai.oauth2.mapper.AuthorizationCodeMapper;
import com.logai.user.entity.User;
import com.logai.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2AuthorizationService {

    private final AuthorizationCodeMapper authorizationCodeMapper;
    private final UserMapper userMapper;

    /**
     * 创建授权码
     */
    public AuthorizationCode createAuthorizationCode(AuthorizationRequest request) {
        User user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw BusinessException.OAuth2Exception("invalid_user", "User does not exist");
        }
        String code = generateAuthorizationCode();

        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCode(code);
        authCode.setClientId(request.getClientId());
        authCode.setUserId(request.getUserId());
        authCode.setUserUuid(user.getUuid());
        authCode.setScope(request.getScope());
        authCode.setRedirectUri(request.getRedirectUri());
        authCode.setCodeChallenge(request.getCodeChallenge());
        authCode.setCodeChallengeMethod(request.getCodeChallengeMethod());
        authCode.setState(request.getState());
        authCode.setCreatedAt(LocalDateTime.now());
        authCode.setExpiresAt(LocalDateTime.now().plusMinutes(10)); // 10分钟过期
        authCode.setUsed(false);
        authorizationCodeMapper.insert(authCode);
        return authorizationCodeMapper.selectById(authCode.getId());
    }

    /**
     * 验证授权码
     */
    public AuthorizationCode validateAuthorizationCode(String code, String clientId,
                                                       String redirectUri, String codeVerifier) {
        AuthorizationCode authCode = authorizationCodeMapper.findByCode(code);
        if (authCode == null) {
            throw BusinessException.OAuth2Exception("invalid_grant", "Authorization code does not exist"); // 授权码不存在
        }
        // 验证授权码是否已使用
        if (authCode.isUsed()) {
            throw BusinessException.OAuth2Exception("invalid_grant", "Authorization code has been used"); // 授权码已使用
        }

        // 验证授权码是否过期
        if (authCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.OAuth2Exception("invalid_grant", "Authorization code has expired"); // 授权码已过期
        }

        // 验证客户端ID
        if (!authCode.getClientId().equals(clientId)) {
            throw BusinessException.OAuth2Exception("invalid_grant", "Client ID does not match"); // 客户端ID不匹配
        }

        // 验证重定向URI
        if (!authCode.getRedirectUri().equals(redirectUri)) {
            throw BusinessException.OAuth2Exception("invalid_grant", "Redirect URI does not match"); // 重定向URI不匹配
        }

        // 验证PKCE（如果启用）
        if (!StringUtils.isEmpty(authCode.getCodeChallenge())) {
            if (codeVerifier == null) {
                throw BusinessException.OAuth2Exception("invalid_grant", "Code verifier is required"); // 需要代码验证器
            }

            String calculatedChallenge = calculateCodeChallenge(codeVerifier, authCode.getCodeChallengeMethod());
            if (!calculatedChallenge.equals(authCode.getCodeChallenge())) {
                throw BusinessException.OAuth2Exception("invalid_grant", "Code verifier does not match"); // 代码验证器不匹配
            }
        }

        // 标记授权码为已使用
        authCode.setUsed(true);
        authCode.setUsedTime(LocalDateTime.now());

        authorizationCodeMapper.updateById(authCode);

        return authorizationCodeMapper.selectById(authCode.getId());
    }


    private String generateAuthorizationCode() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] code = new byte[32];
        secureRandom.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String calculateCodeChallenge(String codeVerifier, String method) {
        try {
            if ("plain".equals(method)) {
                return codeVerifier;
            } else if ("S256".equals(method)) {
                byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
                java.security.MessageDigest messageDigest = java.security.MessageDigest.getInstance("SHA-256");
                messageDigest.update(bytes, 0, bytes.length);
                byte[] digest = messageDigest.digest();
                return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            } else {
                throw BusinessException.OAuth2Exception("invalid_request", "Unsupported code challenge method"); // 不支持的代码挑战方法
            }
        } catch (Exception e) {
            throw BusinessException.OAuth2Exception("invalid_request", "Failed to calculate code challenge"); // 代码挑战计算失败
        }
    }
}