package com.logai.security.service;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCacheService {


    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${token.cache.access-token-ttl:900}") // 15分钟
    private long accessTokenTtl;

    @Value("${token.cache.refresh-token-ttl:604800}") // 7天
    private long refreshTokenTtl;

    private static final String ACCESS_TOKEN_KEY_PREFIX = "access_token:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";
    private static final String USER_TOKENS_KEY_PREFIX = "user_tokens:";
    private static final String DEVICE_TOKEN_KEY_PREFIX = "device_token:";

    /**
     * 缓存Access Token信息
     */
    public void cacheAccessToken(String tokenHash, TokenInfo tokenInfo) {
        String key = ACCESS_TOKEN_KEY_PREFIX + tokenHash;
        long ttl = Math.min(tokenInfo.getRemainingTime(), accessTokenTtl);

        redisTemplate.opsForValue().set(key, tokenInfo, ttl, TimeUnit.SECONDS);

        // 同时缓存用户Token映射
        cacheUserTokenMapping(tokenInfo.getUserUuid(), tokenHash, false);

        log.info("JWT 缓存服务缓存Access Token信息, 用户UUID: {}, 哈希值: {}, TTL: {}s",
                tokenInfo.getUserUuid(), tokenHash, ttl);
    }

    /**
     * 缓存Refresh Token信息
     */
    public void cacheRefreshToken(String tokenHash, TokenInfo tokenInfo) {
        String key = REFRESH_TOKEN_KEY_PREFIX + tokenHash;
        long ttl = Math.min(tokenInfo.getRemainingTime(), refreshTokenTtl);

        redisTemplate.opsForValue().set(key, tokenInfo, ttl, TimeUnit.SECONDS);

        // 同时缓存用户Token映射
        cacheUserTokenMapping(tokenInfo.getUserUuid(), tokenHash, true);

        // 缓存设备Token映射
        if (tokenInfo.getDeviceId() != null) {
            cacheDeviceTokenMapping(tokenInfo.getDeviceId(), tokenHash);
        }

        log.info("JWT 缓存服务缓存Refresh Token信息, 用户UUID: {}, 哈希值: {}, TTL: {}s",
                tokenInfo.getUserUuid(), tokenHash, ttl);
    }

    /**
     * 从缓存获取Access Token信息
     */
    public TokenInfo getAccessTokenInfo(String tokenHash) {
        String key = ACCESS_TOKEN_KEY_PREFIX + tokenHash;
        TokenInfo tokenInfo = (TokenInfo) redisTemplate.opsForValue().get(key);

        if (tokenInfo != null) {
            tokenInfo.updateAccessInfo();
            log.debug("JWT 缓存服务从缓存中获取Access Token信息, 哈希值: {}", tokenHash);
        }

        return tokenInfo;
    }

    /**
     * 从缓存获取Refresh Token信息
     */
    public TokenInfo getRefreshTokenInfo(String tokenHash) {
        String key = REFRESH_TOKEN_KEY_PREFIX + tokenHash;
        TokenInfo tokenInfo = (TokenInfo) redisTemplate.opsForValue().get(key);

        if (tokenInfo != null) {
            tokenInfo.updateAccessInfo();
            log.debug("JWT 缓存服务从缓存中获取Refresh Token信息, 哈希值: {}", tokenHash);
        }

        return tokenInfo;
    }

    /**
     * 删除Access Token缓存
     */
    public void removeAccessToken(String tokenHash) {
        String key = ACCESS_TOKEN_KEY_PREFIX + tokenHash;
        redisTemplate.delete(key);
        log.info("JWT 缓存服务删除Access Token缓存, 哈希值: {}", tokenHash);
    }

    /**
     * 删除Refresh Token缓存
     */
    public void removeRefreshToken(String tokenHash) {
        String key = REFRESH_TOKEN_KEY_PREFIX + tokenHash;
        redisTemplate.delete(key);

        // 删除设备Token映射
        removeDeviceTokenMapping(tokenHash);

        log.info("JWT 缓存服务删除Refresh Token缓存, 哈希值: {}", tokenHash);
    }

    /**
     * 缓存用户Token映射
     */
    private void cacheUserTokenMapping(String userUuid, String tokenHash, boolean isRefreshToken) {
        String key = USER_TOKENS_KEY_PREFIX + userUuid;
        String field = isRefreshToken ? "refresh:" + tokenHash : "access:" + tokenHash;

        redisTemplate.opsForHash().put(key, field, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        redisTemplate.expire(key, refreshTokenTtl, TimeUnit.SECONDS);
    }

    /**
     * 缓存设备Token映射
     */
    private void cacheDeviceTokenMapping(String deviceId, String tokenHash) {
        String key = DEVICE_TOKEN_KEY_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, tokenHash, refreshTokenTtl, TimeUnit.SECONDS);
    }

    /**
     * 删除设备Token映射
     */
    private void removeDeviceTokenMapping(String tokenHash) {
        // 注意：这里需要遍历所有设备来找到对应的映射
        // 实际实现中可以通过维护反向映射表来优化
        String pattern = DEVICE_TOKEN_KEY_PREFIX + "*";
        redisTemplate.keys(pattern).forEach(key -> {
            String cachedTokenHash = (String) redisTemplate.opsForValue().get(key);
            if (tokenHash.equals(cachedTokenHash)) {
                redisTemplate.delete(key);
                log.debug("JWT 缓存服务删除设备Token映射, 哈希值: {}", tokenHash);
            }
        });
    }

    /**
     * 获取用户的所有Token哈希
     */
    public List<String> getUserTokenHashes(Long userId) {
        String key = USER_TOKENS_KEY_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        return entries.keySet().stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    /**
     * 根据设备ID获取Token哈希
     */
    public String getTokenHashByDeviceId(String deviceId) {
        String key = DEVICE_TOKEN_KEY_PREFIX + deviceId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除用户的所有Token缓存
     */
    public void removeAllUserTokens(String userUuid) {
        String key = USER_TOKENS_KEY_PREFIX + userUuid;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        // 删除所有相关的Token缓存
        entries.forEach((field, timestamp) -> {
            String fieldStr = field.toString();
            String tokenHash = fieldStr.substring(fieldStr.indexOf(":") + 1);

            if (fieldStr.startsWith("access:")) {
                removeAccessToken(tokenHash);
            } else if (fieldStr.startsWith("refresh:")) {
                removeRefreshToken(tokenHash);
            }
        });

        // 删除用户Token映射
        redisTemplate.delete(key);
        log.info("JWT 缓存服务删除用户所有Token缓存, 用户UUID: {}", userUuid);
    }

    /**
     * 检查Token是否在黑名单中
     */
    public boolean isTokenBlacklisted(String tokenHash) {
        String key = "token_blacklist:" + tokenHash;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 将Token加入黑名单
     */
    public void addToBlacklist(String tokenHash, long ttlSeconds) {
        String key = "token_blacklist:" + tokenHash;
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        log.info("JWT 缓存服务将Token加入黑名单, 哈希值: {}", tokenHash);
    }

    /**
     * 检查Token是否需要刷新（基于访问次数或时间）
     */
    public boolean shouldRefreshToken(TokenInfo tokenInfo) {
        if (tokenInfo.getAccessCount() == null || tokenInfo.getAccessCount() < 100) {
            return false;
        }

        // 如果访问次数过多，建议刷新
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
}
