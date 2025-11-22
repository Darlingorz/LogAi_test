package com.logai.oauth2.service;

import com.logai.security.dto.TokenInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * OAuth2 Token 缓存服务
 * 在原有Token缓存机制基础上增加clientId维度，避免不同客户端之间的Token冲突
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Oauth2TokenCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${token.cache.access-token-ttl:900}") // 15分钟
    private long accessTokenTtl;

    @Value("${token.cache.refresh-token-ttl:604800}") // 7天
    private long refreshTokenTtl;

    private static final String ACCESS_TOKEN_KEY_PREFIX = "oauth2:access_token:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "oauth2:refresh_token:";
    private static final String USER_TOKENS_KEY_PREFIX = "oauth2:user_tokens:";
    private static final String DEVICE_TOKEN_KEY_PREFIX = "oauth2:device_token:";
    private static final String TOKEN_BLACKLIST_KEY_PREFIX = "oauth2:token_blacklist:";

    /**
     * 缓存Access Token信息
     */
    public void cacheAccessToken(String clientId, String tokenHash, TokenInfo tokenInfo) {
        String key = buildAccessTokenKey(clientId, tokenHash);
        long ttl = Math.min(tokenInfo.getRemainingTime(), accessTokenTtl);

        redisTemplate.opsForValue().set(key, tokenInfo, ttl, TimeUnit.SECONDS);

        // 同步维护用户Token映射
        cacheUserTokenMapping(clientId, tokenInfo.getUserUuid(), tokenHash, false);

        log.info("OAuth2缓存服务缓存Access Token, clientId: {}, 用户UUID: {}, 哈希值: {}, TTL: {}s",
                clientId, tokenInfo.getUserUuid(), tokenHash, ttl);
    }

    /**
     * 缓存Refresh Token信息
     */
    public void cacheRefreshToken(String clientId, String tokenHash, TokenInfo tokenInfo) {
        String key = buildRefreshTokenKey(clientId, tokenHash);
        long ttl = Math.min(tokenInfo.getRemainingTime(), refreshTokenTtl);

        redisTemplate.opsForValue().set(key, tokenInfo, ttl, TimeUnit.SECONDS);

        cacheUserTokenMapping(clientId, tokenInfo.getUserUuid(), tokenHash, true);

        if (tokenInfo.getDeviceId() != null) {
            cacheDeviceTokenMapping(clientId, tokenInfo.getDeviceId(), tokenHash);
        }

        log.info("OAuth2缓存服务缓存Refresh Token, clientId: {}, 用户UUID: {}, 哈希值: {}, TTL: {}s",
                clientId, tokenInfo.getUserUuid(), tokenHash, ttl);
    }

    /**
     * 从缓存获取Access Token信息
     */
    public TokenInfo getAccessTokenInfo(String clientId, String tokenHash) {
        String key = buildAccessTokenKey(clientId, tokenHash);
        TokenInfo tokenInfo = (TokenInfo) redisTemplate.opsForValue().get(key);

        if (tokenInfo != null) {
            tokenInfo.updateAccessInfo();
            log.debug("OAuth2缓存服务读取Access Token, clientId: {}, 哈希值: {}", clientId, tokenHash);
        }

        return tokenInfo;
    }

    /**
     * 从缓存获取Refresh Token信息
     */
    public TokenInfo getRefreshTokenInfo(String clientId, String tokenHash) {
        String key = buildRefreshTokenKey(clientId, tokenHash);
        TokenInfo tokenInfo = (TokenInfo) redisTemplate.opsForValue().get(key);

        if (tokenInfo != null) {
            tokenInfo.updateAccessInfo();
            log.debug("OAuth2缓存服务读取Refresh Token, clientId: {}, 哈希值: {}", clientId, tokenHash);
        }

        return tokenInfo;
    }

    /**
     * 删除Access Token缓存
     */
    public void removeAccessToken(String clientId, String tokenHash) {
        String key = buildAccessTokenKey(clientId, tokenHash);
        redisTemplate.delete(key);
        log.info("OAuth2缓存服务删除Access Token, clientId: {}, 哈希值: {}", clientId, tokenHash);
    }

    /**
     * 删除Refresh Token缓存
     */
    public void removeRefreshToken(String clientId, String tokenHash) {
        String key = buildRefreshTokenKey(clientId, tokenHash);
        redisTemplate.delete(key);

        removeDeviceTokenMapping(clientId, tokenHash);

        log.info("OAuth2缓存服务删除Refresh Token, clientId: {}, 哈希值: {}", clientId, tokenHash);
    }

    /**
     * 获取指定客户端下用户的所有Token哈希
     */
    public List<String> getUserTokenHashes(String clientId, String userUuid) {
        String key = buildUserTokensKey(clientId, userUuid);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        return entries.keySet().stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    /**
     * 根据设备ID获取Token哈希
     */
    public String getTokenHashByDeviceId(String clientId, String deviceId) {
        String key = buildDeviceTokenKey(clientId, deviceId);
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除指定客户端下用户的所有Token缓存
     */
    public void removeAllUserTokens(String clientId, String userUuid) {
        String key = buildUserTokensKey(clientId, userUuid);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        entries.forEach((field, timestamp) -> {
            String fieldStr = field.toString();
            String tokenHash = fieldStr.substring(fieldStr.indexOf(':') + 1);

            if (fieldStr.startsWith("access:")) {
                removeAccessToken(clientId, tokenHash);
            } else if (fieldStr.startsWith("refresh:")) {
                removeRefreshToken(clientId, tokenHash);
            }
        });

        redisTemplate.delete(key);
        log.info("OAuth2缓存服务删除用户Token, clientId: {}, 用户UUID: {}", clientId, userUuid);
    }

    /**
     * 删除用户在所有客户端下的Token缓存
     */
    public void removeAllUserTokens(String userUuid) {
        Set<String> userKeys = redisTemplate.keys(USER_TOKENS_KEY_PREFIX + userUuid + ":*");
        if (userKeys == null || userKeys.isEmpty()) {
            return;
        }

        userKeys.forEach(key -> {
            String clientId = key.substring(key.lastIndexOf(':') + 1);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

            entries.forEach((field, timestamp) -> {
                String fieldStr = field.toString();
                String tokenHash = fieldStr.substring(fieldStr.indexOf(':') + 1);

                if (fieldStr.startsWith("access:")) {
                    removeAccessToken(clientId, tokenHash);
                } else if (fieldStr.startsWith("refresh:")) {
                    removeRefreshToken(clientId, tokenHash);
                }
            });

            redisTemplate.delete(key);
        });

        log.info("OAuth2缓存服务删除用户在所有客户端的Token, 用户UUID: {}", userUuid);
    }

    /**
     * 检查Token是否在黑名单中
     */
    public boolean isTokenBlacklisted(String clientId, String tokenHash) {
        String key = buildBlacklistKey(clientId, tokenHash);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 将Token加入黑名单
     */
    public void addToBlacklist(String clientId, String tokenHash, long ttlSeconds) {
        String key = buildBlacklistKey(clientId, tokenHash);
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        log.info("OAuth2缓存服务将Token加入黑名单, clientId: {}, 哈希值: {}", clientId, tokenHash);
    }

    /**
     * 检查Token是否需要刷新（基于访问次数或时间）
     */
    public boolean shouldRefreshToken(TokenInfo tokenInfo) {
        if (tokenInfo.getAccessCount() == null || tokenInfo.getAccessCount() < 100) {
            return false;
        }

        LocalDateTime lastAccessed = tokenInfo.getLastAccessedAt();
        if (lastAccessed != null) {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            return lastAccessed.isBefore(oneHourAgo);
        }

        return false;
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Long> getCacheStats() {
        Map<String, Long> stats = new HashMap<>();

        String accessTokenPattern = ACCESS_TOKEN_KEY_PREFIX + "*";
        String refreshTokenPattern = REFRESH_TOKEN_KEY_PREFIX + "*";
        String userTokensPattern = USER_TOKENS_KEY_PREFIX + "*";

        stats.put("accessTokenCount", (long) redisTemplate.keys(accessTokenPattern).size());
        stats.put("refreshTokenCount", (long) redisTemplate.keys(refreshTokenPattern).size());
        stats.put("userTokenMappings", (long) redisTemplate.keys(userTokensPattern).size());

        return stats;
    }

    private void cacheUserTokenMapping(String clientId, String userUuid, String tokenHash, boolean isRefreshToken) {
        String key = buildUserTokensKey(clientId, userUuid);
        String field = (isRefreshToken ? "refresh:" : "access:") + tokenHash;

        redisTemplate.opsForHash().put(key, field, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        redisTemplate.expire(key, refreshTokenTtl, TimeUnit.SECONDS);
    }

    private void cacheDeviceTokenMapping(String clientId, String deviceId, String tokenHash) {
        String key = buildDeviceTokenKey(clientId, deviceId);
        redisTemplate.opsForValue().set(key, tokenHash, refreshTokenTtl, TimeUnit.SECONDS);
    }

    private void removeDeviceTokenMapping(String clientId, String tokenHash) {
        String pattern = DEVICE_TOKEN_KEY_PREFIX + clientId + ":*";
        Set<String> deviceKeys = redisTemplate.keys(pattern);
        if (deviceKeys == null) {
            return;
        }

        deviceKeys.forEach(key -> {
            String cachedTokenHash = (String) redisTemplate.opsForValue().get(key);
            if (tokenHash.equals(cachedTokenHash)) {
                redisTemplate.delete(key);
                log.debug("OAuth2缓存服务删除设备Token映射, clientId: {}, 哈希值: {}", clientId, tokenHash);
            }
        });
    }

    private String buildAccessTokenKey(String clientId, String tokenHash) {
        return ACCESS_TOKEN_KEY_PREFIX + clientId + ':' + tokenHash;
    }

    private String buildRefreshTokenKey(String clientId, String tokenHash) {
        return REFRESH_TOKEN_KEY_PREFIX + clientId + ':' + tokenHash;
    }

    private String buildUserTokensKey(String clientId, String userUuid) {
        return USER_TOKENS_KEY_PREFIX + userUuid + ':' + clientId;
    }

    private String buildDeviceTokenKey(String clientId, String deviceId) {
        return DEVICE_TOKEN_KEY_PREFIX + clientId + ':' + deviceId;
    }

    private String buildBlacklistKey(String clientId, String tokenHash) {
        return TOKEN_BLACKLIST_KEY_PREFIX + clientId + ':' + tokenHash;
    }
}

