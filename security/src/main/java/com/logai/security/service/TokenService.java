package com.logai.security.service;

import com.logai.security.dto.RefreshTokenRequest;
import com.logai.security.dto.TokenInfo;
import com.logai.security.dto.TokenResponse;
import com.logai.security.encryption.TokenEncryptionService;
import com.logai.security.entity.RefreshToken;
import com.logai.security.mapper.RefreshTokenMapper;
import com.logai.security.util.JwtUtils;
import com.logai.user.entity.User;
import com.logai.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {


    private final JwtUtils jwtUtils;
    private final TokenEncryptionService encryptionService;
    private final TokenCacheService tokenCacheService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;
    private final TransactionalOperator transactionalOperator;

    @Value("${token.jwt.secret}")
    private String jwtSecret;

    @Value("${token.jwt.access-token-expiration:900}") // 15分钟
    private int accessTokenExpiration;

    @Value("${token.jwt.refresh-token-expiration:604800}") // 7天
    private int refreshTokenExpiration;

    @Value("${token.max-devices-per-user:5}")
    private int maxDevicesPerUser;

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private SecretKey getJwtSigningKey() {
        // Base64解码密钥
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);

        // 确保密钥长度符合HS512要求（至少512位）
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException("JWT secret key must be at least 512 bits (64 bytes) for HS512 algorithm");
        }

        // 创建HS512密钥
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成Access Token和Refresh Token
     */
    public TokenResponse generateTokens(User user, String deviceId, String ipAddress, String userAgent) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);

        // 生成Access Token
        String accessToken = generateAccessToken(user);
        String accessTokenId = extractTokenId(accessToken);

        // 生成Refresh Token
        String refreshTokenValue = encryptionService.generateSecureToken();
        String refreshTokenHash = encryptionService.generateTokenHash(refreshTokenValue);

        // 检查设备数量限制
        checkDeviceLimit(user.getId());

        RefreshToken refreshToken = createRefreshTokenEntity(user, refreshTokenHash, normalizedDeviceId, ipAddress, userAgent);

        refreshTokenMapper.insertOrUpdate(refreshToken);

        cacheTokens(accessTokenId, refreshTokenHash, user, normalizedDeviceId);
        log.info("Generated tokens for user: {}, device: {}", user.getId(), normalizedDeviceId);
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .accessTokenExpiresIn((long) accessTokenExpiration)
                .refreshTokenExpiresIn((long) refreshTokenExpiration)
                .tokenType("Bearer")
                .userInfo(buildUserInfo(user))
                .build();

    }

    /**
     * 刷新Access Token
     */
    @Transactional
    public TokenResponse refreshAccessToken(RefreshTokenRequest request) {
        String refreshTokenValue = request.getRefreshToken();
        String refreshTokenHash = encryptionService.generateTokenHash(refreshTokenValue);
        String normalizedDeviceId = normalizeDeviceId(request.getDeviceId());

        log.info("Processing token refresh for device: {}", normalizedDeviceId);

        // 先从缓存查找
        TokenInfo cachedTokenInfo = tokenCacheService.getRefreshTokenInfo(refreshTokenHash);

        if (cachedTokenInfo != null) {
            return processCachedRefreshToken(cachedTokenInfo, refreshTokenHash, normalizedDeviceId);
        }

        RefreshToken refreshToken = refreshTokenMapper.findValidByTokenHash(refreshTokenHash, LocalDateTime.now());
        if (refreshToken == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }
        String decryptedToken = encryptionService.decryptToken(refreshToken.getEncryptedToken());
        if (!refreshTokenValue.equals(decryptedToken)) {
            throw new RuntimeException("Token verification failed");
        }

        // 更新最后使用时间
        refreshToken.updateLastUsed();
        refreshTokenMapper.insertOrUpdate(refreshToken);

        User user = userMapper.findByUuid(refreshToken.getUserUuid());
        if (user == null) {
            throw new RuntimeException("User not found: " + refreshToken.getUserUuid());
        }

        int userRole = normalizeRole(user.getRole(), user.getUuid());
        String newAccessToken = generateAccessToken(user.getUuid(), userRole);
        cacheNewAccessToken(newAccessToken, user.getUuid(), userRole, normalizedDeviceId);

        // 更新缓存中的Refresh Token信息
        updateRefreshTokenCache(refreshTokenHash, userRole, normalizedDeviceId);

        log.info("Token refreshed successfully for user: {}", refreshToken.getUserUuid());

        return TokenResponse.accessOnly(newAccessToken, (long) accessTokenExpiration);
    }

    /**
     * 处理缓存中的Refresh Token刷新
     */
    private TokenResponse processCachedRefreshToken(TokenInfo cachedTokenInfo,
                                                    String refreshTokenHash,
                                                    String deviceId) {
        if (cachedTokenInfo.isExpired()) {
            throw new RuntimeException("Refresh token expired");
        }

        // 验证设备ID
        String normalizedRequestDeviceId = normalizeDeviceId(deviceId);
        String normalizedCachedDeviceId = normalizeDeviceId(cachedTokenInfo.getDeviceId());
        if (normalizedRequestDeviceId != null && !normalizedRequestDeviceId.equals(normalizedCachedDeviceId)) {
            log.warn("Device ID mismatch for token refresh. Expected: {}, Actual: {}",
                    normalizedCachedDeviceId, normalizedRequestDeviceId);
        }

        Integer role = cachedTokenInfo.getRole() != null
                ? normalizeRole(cachedTokenInfo.getRole(), cachedTokenInfo.getUserUuid())
                : resolveUserRole(cachedTokenInfo.getUserUuid());

        String newAccessToken = generateAccessToken(cachedTokenInfo.getUserUuid(), role);

        // 更新访问信息
        cacheNewAccessToken(newAccessToken, cachedTokenInfo.getUserUuid(), role, normalizedRequestDeviceId);
        cachedTokenInfo.setRole(role);
        if (cachedTokenInfo.getDeviceId() == null && normalizedRequestDeviceId != null) {
            cachedTokenInfo.setDeviceId(normalizedRequestDeviceId);
        }
        cachedTokenInfo.updateAccessInfo();
        tokenCacheService.cacheRefreshToken(refreshTokenHash, cachedTokenInfo);

        log.info("Token refreshed from cache for user: {}", cachedTokenInfo.getUserUuid());

        return TokenResponse.accessOnly(newAccessToken, (long) accessTokenExpiration);
    }

    /**
     * 验证Access Token
     */
    public TokenInfo validateAccessToken(String accessToken) {
        try {
            // 验证JWT格式和签名
            if (!jwtUtils.validateToken(accessToken)) {
                return null;
            }

            String tokenId = extractTokenId(accessToken);
            String tokenHash = encryptionService.generateTokenHash(tokenId);

            // 检查黑名单
            if (tokenCacheService.isTokenBlacklisted(tokenHash)) {
                log.warn("Access token is blacklisted: {}", tokenHash);
                return null;
            }

            // 从缓存获取Token信息
            TokenInfo tokenInfo = tokenCacheService.getAccessTokenInfo(tokenHash);
            if (tokenInfo != null) {
                return tokenInfo;
            }

            // 缓存未命中，说明Token可能已过期或无效
            log.debug("Access token not found in cache: {}", tokenHash);
            return null;

        } catch (Exception e) {
            log.error("Failed to validate access token", e);
            return null;
        }
    }

    /**
     * 废除Refresh Token
     */
    @Transactional
    public void revokeRefreshToken(String refreshTokenValue, String reason) {
        String refreshTokenHash = encryptionService.generateTokenHash(refreshTokenValue);
        RefreshToken refreshToken = refreshTokenMapper.findValidByTokenHash(refreshTokenHash, LocalDateTime.now());
        refreshToken.revoke(reason);
        if (refreshTokenMapper.insertOrUpdate(refreshToken)) {
            tokenCacheService.removeRefreshToken(refreshTokenHash);
            // 加入黑名单
            long blacklistTtl = refreshToken.getExpiresAt().toEpochSecond(ZoneOffset.UTC) -
                    LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            tokenCacheService.addToBlacklist(refreshTokenHash, blacklistTtl);

            log.info("Refresh token revoked: {}, reason: {}", refreshTokenHash, reason);
        }
    }

    /**
     * 废除用户的所有Token
     */
    @Transactional
    public void revokeAllUserTokens(Long userId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        Integer count = refreshTokenMapper.revokeAllByUserId(userId, now, reason);
        User user = userMapper.selectById(userId);
        tokenCacheService.removeAllUserTokens(user.getUuid());
        log.info("Revoked {} tokens for user: {}, reason: {}", count, userId, reason);
    }

    /**
     * 检查设备数量限制
     */
    private void checkDeviceLimit(Long userId) {
        Long count = refreshTokenMapper.countValidByUserId(userId, LocalDateTime.now());
        if (count >= maxDevicesPerUser) {
            refreshTokenMapper.findValidByUserId(userId, LocalDateTime.now())
                    .stream()
                    .sorted(Comparator.comparing(RefreshToken::getLastUsedAt))
                    .skip(maxDevicesPerUser - 1)
                    .forEach(oldToken -> {
                        oldToken.revoke("Device limit exceeded");
                        refreshTokenMapper.insertOrUpdate(oldToken);
                    });
        }
    }

    /**
     * 创建Refresh Token实体
     */
    private RefreshToken createRefreshTokenEntity(User user, String tokenHash, String deviceId,
                                                  String ipAddress, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(refreshTokenExpiration);
        // 加密Token值
        String encryptedToken = encryptionService.encryptToken(tokenHash);
        String normalizedDeviceId = normalizeDeviceId(deviceId);

        return RefreshToken.builder()
                .tokenHash(tokenHash)
                .encryptedToken(encryptedToken)
                .userUuid(user.getUuid())
                .userId(user.getId())
                .deviceId(normalizedDeviceId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(now)
                .expiresAt(expiresAt)
                .lastUsedAt(now)
                .isRevoked(false)
                .build();
    }

    /**
     * 生成Access Token（JWT）
     */
    private String generateAccessToken(User user) {
        return generateAccessToken(user.getUuid(), user.getRole());
    }

    private String generateAccessToken(String userUuid, Integer role) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + accessTokenExpiration * 1000L);
        int normalizedRole = normalizeRole(role, userUuid);

        return Jwts.builder()
                .setSubject(userUuid)
                .claim("role", normalizedRole)
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("tokenId", UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(getJwtSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 提取Token ID
     * 从JWT Token的payload中提取tokenId字段
     */
    private String extractTokenId(String accessToken) {
        try {
            // 解析JWT Token获取claims
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getJwtSigningKey())
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();

            // 从claims中获取tokenId字段
            String tokenId = claims.get("tokenId", String.class);
            if (tokenId != null) {
                return tokenId;
            }

            // 如果tokenId不存在，使用JWT ID (jti) 字段
            tokenId = claims.getId();
            if (tokenId != null) {
                return tokenId;
            }

            // 如果都没有，使用subject + issuedAt组合作为唯一标识
            String subject = claims.getSubject();
            Date issuedAt = claims.getIssuedAt();
            if (subject != null && issuedAt != null) {
                return subject + "_" + issuedAt.getTime();
            }

            // 最后的备选方案：使用Token的前32个字符
            log.warn("No tokenId found in JWT, using substring fallback");
            return accessToken.substring(0, Math.min(accessToken.length(), 32));

        } catch (Exception e) {
            log.error("Failed to extract token ID from JWT, using fallback", e);
            // 如果解析失败，使用Token的前32个字符作为ID
            return accessToken.substring(0, Math.min(accessToken.length(), 32));
        }
    }

    /**
     * 缓存Token信息
     */
    private void cacheTokens(String accessTokenId, String refreshTokenHash, User user, String deviceId) {
        LocalDateTime now = LocalDateTime.now();
        int normalizedRole = normalizeRole(user.getRole(), user.getUuid());
        String normalizedDeviceId = normalizeDeviceId(deviceId);

        // 生成Access Token的hash用于缓存key
        String accessTokenHash = encryptionService.generateTokenHash(accessTokenId);

        // 缓存Access Token
        TokenInfo accessTokenInfo = TokenInfo.builder()
                .userUuid(user.getUuid())
                .tokenHash(accessTokenId)
                .role(normalizedRole)
                .deviceId(normalizedDeviceId)
                .createdAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpiration))
                .lastAccessedAt(now)
                .accessCount(0)
                .isValid(true)
                .build();

        tokenCacheService.cacheAccessToken(accessTokenHash, accessTokenInfo);

        // 缓存Refresh Token
        TokenInfo refreshTokenInfo = TokenInfo.builder()
                .userUuid(user.getUuid())
                .tokenHash(refreshTokenHash)
                .role(normalizedRole)
                .deviceId(normalizedDeviceId)
                .createdAt(now)
                .expiresAt(now.plusSeconds(refreshTokenExpiration))
                .lastAccessedAt(now)
                .accessCount(0)
                .isValid(true)
                .build();

        tokenCacheService.cacheRefreshToken(refreshTokenHash, refreshTokenInfo);
    }

    /**
     * 缓存新的Access Token
     */
    private void cacheNewAccessToken(String newAccessToken, String userId, Integer role, String deviceId) {
        String accessTokenId = extractTokenId(newAccessToken);
        String accessTokenHash = encryptionService.generateTokenHash(accessTokenId);
        int normalizedRole = normalizeRole(role, userId);
        String normalizedDeviceId = normalizeDeviceId(deviceId);

        TokenInfo accessTokenInfo = TokenInfo.builder()
                .userUuid(userId)
                .tokenHash(accessTokenId)  // 存储原始tokenId用于验证
                .role(normalizedRole)
                .deviceId(normalizedDeviceId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(accessTokenExpiration))
                .lastAccessedAt(LocalDateTime.now())
                .accessCount(0)
                .isValid(true)
                .build();

        tokenCacheService.cacheAccessToken(accessTokenHash, accessTokenInfo);
    }

    /**
     * 更新Refresh Token缓存
     */
    private void updateRefreshTokenCache(String refreshTokenHash, Integer role, String deviceId) {
        TokenInfo existingInfo = tokenCacheService.getRefreshTokenInfo(refreshTokenHash);
        if (existingInfo != null) {
            existingInfo.updateAccessInfo();
            if (role != null) {
                existingInfo.setRole(role);
            }
            String normalizedDeviceId = normalizeDeviceId(deviceId);
            if (normalizedDeviceId != null) {
                existingInfo.setDeviceId(normalizedDeviceId);
            }
            tokenCacheService.cacheRefreshToken(refreshTokenHash, existingInfo);
        }
    }

    private Integer resolveUserRole(String userUuid) {
        User user = userMapper.findByUuid(userUuid);
        if (user == null) {
            throw new RuntimeException("User not found: " + userUuid);
        }
        return normalizeRole(user.getRole(), user.getUuid());
    }

    private int normalizeRole(Integer role, String userUuid) {
        if (role == null) {
            log.warn("User {} has no role assigned, defaulting to 0", userUuid);
            return 0;
        }
        return role;
    }

    private String normalizeDeviceId(String deviceId) {
        return StringUtils.hasText(deviceId) ? deviceId : null;
    }

    /**
     * 构建用户信息
     */
    private TokenResponse.UserInfo buildUserInfo(User user) {
        return TokenResponse.UserInfo.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .build();
    }

    /**
     * 获取用户所有有效的Refresh Token
     */
    public List<RefreshToken> getUserActiveTokens(Long userId) {
        return refreshTokenMapper.findValidByUserId(userId, LocalDateTime.now());
    }

    /**
     * 清理过期Token
     */
    public Integer cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        // 清理7天前的过期Token
        LocalDateTime cleanupTime = now.minusDays(7);
        return refreshTokenMapper.deleteRevokedTokens(cleanupTime);
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Long> getTokenCacheStats() {
        return tokenCacheService.getCacheStats();
    }
}
