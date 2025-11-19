package com.logai.user.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.logai.security.dto.TokenResponse;
import com.logai.security.service.TokenService;
import com.logai.user.dto.GoogleUserInfo;
import com.logai.user.entity.SocialAccount;
import com.logai.user.entity.User;
import com.logai.user.mapper.SocialAccountMapper;
import com.logai.user.mapper.UserMapper;
import com.logai.user.service.SocialLoginService;
import com.logai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginServiceImpl implements SocialLoginService {

    /**
     * 处理谷歌登录（前端Code Flow）
     */

    private final UserMapper userMapper;
    private final SocialAccountMapper socialAccountMapper;
    private final TokenService tokenService;
    private final UserService userService;

    private final String googleValidationUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.provider.google.token-uri}")
    private String googleTokenUri;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String googleUserInfoUri;

    /**
     * 处理谷歌登录（前端Code Flow）
     */
    @Override
    public User handleGoogleLogin(String credential, String deviceId, String clientIp, String userAgent, String timeZone) {
        GoogleUserInfo googleUserInfo = validateGoogleIdToken(credential);
        return processSocialLogin(
                "google",
                googleUserInfo.getSub(),
                googleUserInfo.getEmail(),
                googleUserInfo.getName(),
                googleUserInfo.getPicture(),
                "",
                "",
                null,
                deviceId,
                clientIp,
                userAgent,
                timeZone
        );
    }

    private GoogleUserInfo validateGoogleIdToken(String idToken) {

        String validationUrl = googleValidationUrl + idToken;

        try {
            // === 1. 调用 Google 服务器解析 token ===
            HttpResponse response = HttpRequest.get(validationUrl)
                    .header("Content-Type", "application/json")
                    .execute();

            int status = response.getStatus();
            String body = response.body();

            // === 2. 模拟 WebClient.onStatus 行为 ===
            if (status >= 400) {
                log.error("Google ID Token validation failed: {}", body);
                throw new RuntimeException("Invalid Google ID Token: " + body);
            }

            // === 3. 解析 JSON 成 GoogleUserInfo ===
            GoogleUserInfo userInfo = JSON.parseObject(body, GoogleUserInfo.class);

            // === 4. 校验 aud 是否为本应用 ===
            if (!googleClientId.equals(userInfo.getAud())) {
                log.error("Token audience (aud) '{}' does not match our client ID.", userInfo.getAud());
                throw new RuntimeException("Token is not intended for this application.");
            }

            log.info("ID Token validated successfully for user: {}", userInfo.getEmail());

            return userInfo;

        } catch (Exception e) {
            log.error("Google ID Token validation error: {}", e.getMessage(), e);
            throw new RuntimeException("Google ID Token validation failed", e);
        }
    }

    /**
     * 处理社交登录核心逻辑
     */
    private User processSocialLogin(String provider, String providerUserId, String email,
                                    String name, String picture, String accessToken,
                                    String refreshToken, Integer expiresIn,
                                    String deviceId, String clientIp, String userAgent, String timeZone) {

        try {
            // 1. 先查是否已有 Social Account
            SocialAccount socialAccount =
                    socialAccountMapper.findByProviderAndProviderUserId(provider, providerUserId);

            User user;

            if (socialAccount != null) {
                // ========== Case A: 社交账号已存在 ==========
                socialAccount.setAccessToken(accessToken);
                if (expiresIn != null) {
                    socialAccount.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                }
                socialAccount.setUpdatedAt(LocalDateTime.now());

                socialAccountMapper.updateById(socialAccount);

                user = userMapper.selectById(socialAccount.getUserId());
                if (user == null) {
                    throw new RuntimeException("Invalid social account: user not found");
                }

            } else {
                // ========== Case B: 社交账号不存在 ==========
                log.info("New social account detected for email: {}", email);

                // 2. 查用户是否已存在
                User existingUser = userMapper.findByEmail(email);

                if (existingUser != null) {
                    // ========== Case B1: 用户存在 → 绑定社交账号 ==========
                    log.info("Binding social account to existing user: {}", email);

                    saveSocialAccount(
                            existingUser.getId(),
                            provider,
                            providerUserId,
                            email,
                            name,
                            picture,
                            accessToken,
                            refreshToken,
                            expiresIn
                    );

                    user = existingUser;

                } else {
                    // ========== Case B2: 用户不存在 → 自动创建 ==========
                    log.info("Auto-registering new user with social account: {}", email);

                    user = createNewUser(
                            email,
                            name,
                            provider,
                            providerUserId,
                            picture,
                            accessToken,
                            refreshToken,
                            expiresIn,
                            timeZone
                    );
                }
            }

            // 3. 生成 Token 并写回 user
            TokenResponse tokenResponse =
                    tokenService.generateTokens(user, deviceId, clientIp, userAgent);

            user.setAccessToken(tokenResponse.getAccessToken());
            user.setRefreshToken(tokenResponse.getRefreshToken());

            log.info("Social login successful for user: {}", user.getEmail());

            return user;

        } catch (Exception e) {
            log.error("Social login failed: {}", e.getMessage());
            throw new RuntimeException("Social login failed: " + e.getMessage(), e);
        }
    }

    private User createNewUser(String email, String name, String provider,
                               String providerUserId, String picture,
                               String accessToken, String refreshToken, Integer expiresIn, String timeZone) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setUsername(name);
        newUser.setPassword("");
        newUser.setStatus("1");
        newUser.setRole(null);
        newUser.setTimeZone(timeZone);
        newUser.setUuid(UUID.randomUUID().toString());
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(newUser);

        User savedUser = userMapper.selectById(newUser.getId());
        saveSocialAccount(savedUser.getId(), provider, providerUserId, email, name, picture, accessToken, refreshToken, expiresIn);
        try {
            userService.startTrial(savedUser);
        } catch (Exception e) {
            log.error("用户{}试用开启失败: {}", savedUser.getEmail(), e.getMessage(), e);
        }
        return savedUser;
    }

    /**
     * 保存社交账号信息
     */
    private SocialAccount saveSocialAccount(Long userId, String provider, String providerUserId,
                                            String email, String name, String picture,
                                            String accessToken, String refreshToken, Integer expiresIn) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUserId(userId);
        socialAccount.setProvider(provider);
        socialAccount.setProviderUserId(providerUserId);
        socialAccount.setEmail(email);
        socialAccount.setName(name);
        socialAccount.setPicture(picture);
        socialAccount.setAccessToken(accessToken);
        socialAccount.setRefreshToken(refreshToken);
        // 默认1小时过期
        if (expiresIn != null) {
            socialAccount.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        } else {
            socialAccount.setExpiresAt(LocalDateTime.now().plusHours(1));
        }
        socialAccount.setCreatedAt(LocalDateTime.now());
        socialAccount.setUpdatedAt(LocalDateTime.now());
        int insert = socialAccountMapper.insert(socialAccount);
        if (insert > 0) {
            log.info("Social account saved for {} user: {}", provider, email);
        }
        return socialAccount;
    }

//    /**
//     * 交换授权码获取访问令牌（Code Flow）
//     */
//    private Mono<GoogleTokenResponse> exchangeCodeForTokens(String credential, String redirectUri) {
//        return webClient.post()
//                .uri(googleTokenUri)
//                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                .bodyValue(buildTokenRequestBody(credential, redirectUri))
//                .retrieve()
//                .onStatus(HttpStatusCode::isError, response ->
//                        response.bodyToMono(String.class)
//                                .flatMap(errorBody -> {
//                                    log.error("Token exchange failed: {}", errorBody);
//                                    return Mono.error(new RuntimeException("Token exchange failed: " + errorBody));
//                                })
//                )
//                .bodyToMono(GoogleTokenResponse.class)
//                .doOnSuccess(response -> log.info("Successfully exchanged code for tokens"));
//    }
//
//    /**
//     * 获取Google用户信息
//     */
//    private Mono<GoogleUserInfo> getGoogleUserInfo(String accessToken) {
//        return webClient.get()
//                .uri(googleUserInfoUri)
//                .headers(headers -> headers.setBearerAuth(accessToken))
//                .retrieve()
//                .onStatus(status -> status.isError(), response ->
//                        response.bodyToMono(String.class)
//                                .flatMap(errorBody -> {
//                                    log.error("Failed to get user info: {}", errorBody);
//                                    return Mono.error(new RuntimeException("Failed to get user info: " + errorBody));
//                                })
//                )
//                .bodyToMono(GoogleUserInfo.class)
//                .doOnSuccess(userInfo -> log.info("Successfully retrieved Google user info for: {}", userInfo.getEmail()));
//    }
//
//    /**
//     * 构建令牌请求体
//     */
//    private String buildTokenRequestBody(String code, String redirectUri) {
//        return String.format(
//                "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
//                code, googleClientId, googleClientSecret, redirectUri
//        );
//    }
}
