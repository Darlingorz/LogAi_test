package com.logai.oauth2.service;

import com.logai.common.exception.BusinessException;
import com.logai.oauth2.dto.OauthTokenResponse;
import com.logai.oauth2.entity.AuthorizationCode;
import com.logai.security.dto.TokenInfo;
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

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth2 Token管理服务
 * 参考通用TokenService的实现，增加clientId维度，实现与OAuth2协议兼容
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final JwtUtils jwtUtils;
    private final TokenEncryptionService encryptionService;
    private final Oauth2TokenCacheService tokenCacheService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;

    @Value("${token.jwt.secret}")
    private String jwtSecret;

    @Value("${token.jwt.access-token-expiration:900}") // 15分钟
    private int accessTokenExpiration;

    @Value("${token.jwt.refresh-token-expiration:604800}") // 7天
    private int refreshTokenExpiration;

    private static final String TOKEN_TYPE_ACCESS = "access";

    /**
     * 使用授权码生成访问令牌和刷新令牌
     */
    @Transactional
    public OauthTokenResponse generateTokens(AuthorizationCode authorizationCode) {
        User user = userMapper.selectById(authorizationCode.getUserId());
        if (user == null) {
            throw BusinessException.OAuth2Exception("invalid_user", "User does not exist");
        }
        return createTokenPair(authorizationCode, user);

    }

    /**
     * 刷新访问令牌
     */
    @Transactional
    public OauthTokenResponse refreshAccessToken(String refreshTokenValue, String clientId) {
        String refreshTokenHash = encryptionService.generateTokenHash(refreshTokenValue);

        TokenInfo cachedTokenInfo = tokenCacheService.getRefreshTokenInfo(clientId, refreshTokenHash);
        if (cachedTokenInfo != null) {
            return processCachedRefreshToken(clientId, cachedTokenInfo, refreshTokenHash, refreshTokenValue);
        }

        RefreshToken refreshToken = refreshTokenMapper.findValidByTokenHashAndClientId(refreshTokenHash, clientId, LocalDateTime.now());
        if (refreshToken == null) {
            throw BusinessException.OAuth2Exception("invalid_grant", "刷新令牌无效或已过期");
        }

        String decryptedToken = encryptionService.decryptToken(refreshToken.getEncryptedToken());
        if (!refreshTokenValue.equals(decryptedToken)) {
            throw BusinessException.OAuth2Exception("invalid_grant", "刷新令牌验证失败");
        }
        refreshToken.updateLastUsed();
        refreshTokenMapper.insertOrUpdate(refreshToken);

        User user = userMapper.findByUuid(refreshToken.getUserUuid());
        if (user == null) {
            throw BusinessException.OAuth2Exception("invalid_user", "用户不存在");
        }
        return buildResponseFromRefreshToken(clientId, refreshToken, user, refreshTokenValue);
    }

    /**
     * 验证访问令牌
     */
    public TokenInfo validateAccessToken(String accessToken) {
        try {
            if (!jwtUtils.validateToken(accessToken)) {
                return null;
            }

            Claims claims = parseClaims(accessToken);
            String tokenId = claims.get("tokenId", String.class);
            String clientId = claims.get("client_id", String.class);

            if (tokenId == null || clientId == null) {
                log.warn("OAuth2访问令牌缺少必要声明: tokenId/client_id");
                return null;
            }

            String tokenHash = encryptionService.generateTokenHash(tokenId);

            if (tokenCacheService.isTokenBlacklisted(clientId, tokenHash)) {
                log.warn("OAuth2访问令牌已被加入黑名单, clientId: {}, hash: {}", clientId, tokenHash);
                return null;
            }

            TokenInfo tokenInfo = tokenCacheService.getAccessTokenInfo(clientId, tokenHash);
            if (tokenInfo != null) {
                return tokenInfo;
            }

            log.debug("OAuth2访问令牌未命中缓存, clientId: {}, hash: {}", clientId, tokenHash);
            return null;
        } catch (Exception e) {
            log.error("OAuth2访问令牌验证失败", e);
            return null;
        }
    }

    /**
     * 废除刷新令牌
     */
    @Transactional
    public void revokeRefreshToken(String refreshTokenValue, String clientId, String reason) {
        String refreshTokenHash = encryptionService.generateTokenHash(refreshTokenValue);
        RefreshToken refreshToken = refreshTokenMapper.findValidByTokenHashAndClientId(refreshTokenHash, clientId, LocalDateTime.now());
        refreshToken.revoke(reason);
        refreshTokenMapper.insertOrUpdate(refreshToken);
        tokenCacheService.removeRefreshToken(clientId, refreshTokenHash);

        long blacklistTtl = refreshToken.getExpiresAt().toEpochSecond(ZoneOffset.UTC) -
                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        tokenCacheService.addToBlacklist(clientId, refreshTokenHash, blacklistTtl);

        log.info("OAuth2刷新令牌已废除, clientId: {}, reason: {}", clientId, reason);
    }

    /**
     * 废除用户的所有Token
     */
    public void revokeAllUserTokens(Long userId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        Integer count = refreshTokenMapper.revokeAllByUserId(userId, now, reason);
        User user = userMapper.selectById(userId);
        tokenCacheService.removeAllUserTokens(user.getUuid());
        log.info("OAuth2用户Token已全部废除, userId: {}, reason: {}, count: {}", userId, reason, count);
    }

    /**
     * 清理过期Token
     */
    public Integer cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cleanupTime = now.minusDays(7);
        Integer count = refreshTokenMapper.deleteRevokedTokens(cleanupTime);
        if (count > 0) {
            log.info("OAuth2清理过期刷新令牌数量: {}", count);
        }
        return count;
    }

    public Map<String, Long> getTokenCacheStats() {
        return tokenCacheService.getCacheStats();
    }

    private OauthTokenResponse createTokenPair(AuthorizationCode authorizationCode, User user) {
        String accessToken = generateAccessToken(user, authorizationCode.getClientId(), authorizationCode.getScope());
        String accessTokenId = extractTokenId(accessToken);

        String refreshTokenValue = encryptionService.generateSecureToken();
        String refreshTokenHash = encryptionService.generateTokenHash(refreshTokenValue);

        RefreshToken refreshToken = createRefreshTokenEntity(user, refreshTokenHash, refreshTokenValue,
                authorizationCode.getClientId(), authorizationCode.getScope());

        refreshTokenMapper.insertOrUpdate(refreshToken);

        cacheTokens(authorizationCode.getClientId(), accessTokenId, refreshTokenHash, user, authorizationCode.getScope());
        return buildTokenResponse(accessToken, refreshTokenValue);
    }


    private OauthTokenResponse processCachedRefreshToken(String clientId, TokenInfo cachedTokenInfo,
                                                         String refreshTokenHash, String refreshTokenValue) {
        if (cachedTokenInfo.isExpired()) {
            throw BusinessException.OAuth2Exception("invalid_grant", "刷新令牌已过期");
        }

        User user = userMapper.findByUuid(cachedTokenInfo.getUserUuid());
        if (user == null) {
            throw BusinessException.OAuth2Exception("invalid_user", "用户不存在");
        }

        String newAccessToken = generateAccessToken(user, clientId, cachedTokenInfo.getScope());

        cacheNewAccessToken(clientId, newAccessToken, user, cachedTokenInfo.getScope());
        cachedTokenInfo.updateAccessInfo();
        tokenCacheService.cacheRefreshToken(clientId, refreshTokenHash, cachedTokenInfo);

        log.info("OAuth2刷新令牌缓存命中, clientId: {}, userUuid: {}", clientId, cachedTokenInfo.getUserUuid());
        return buildTokenResponse(newAccessToken, refreshTokenValue);
    }

    private OauthTokenResponse buildResponseFromRefreshToken(String clientId, RefreshToken refreshToken,
                                                             User user, String refreshTokenValue) {
        String newAccessToken = generateAccessToken(user, clientId, refreshToken.getScope());

        cacheNewAccessToken(clientId, newAccessToken, user, refreshToken.getScope());
        cacheRefreshTokenFromEntity(clientId, refreshToken, user);

        log.info("OAuth2刷新令牌成功, clientId: {}, userUuid: {}", clientId, refreshToken.getUserUuid());

        return buildTokenResponse(newAccessToken, refreshTokenValue);
    }

    private SecretKey getJwtSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException("JWT secret key must be at least 512 bits (64 bytes) for HS512 algorithm");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateAccessToken(User user, String clientId, String scope) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + accessTokenExpiration * 1000L);

        return Jwts.builder()
                .setSubject(user.getUuid())
                .claim("role", user.getRole())
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("tokenId", UUID.randomUUID().toString())
                .claim("client_id", clientId)
                .claim("scope", scope)
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(getJwtSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    private RefreshToken createRefreshTokenEntity(User user, String tokenHash, String refreshTokenValue,
                                                  String clientId, String scope) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(refreshTokenExpiration);

        String encryptedToken = encryptionService.encryptToken(refreshTokenValue);

        return RefreshToken.builder()
                .tokenHash(tokenHash)
                .encryptedToken(encryptedToken)
                .userUuid(user.getUuid())
                .userId(user.getId())
                .clientId(clientId)
                .scope(scope)
                .createdAt(now)
                .expiresAt(expiresAt)
                .lastUsedAt(now)
                .isRevoked(false)
                .build();
    }

    private void cacheTokens(String clientId, String accessTokenId, String refreshTokenHash,
                             User user, String scope) {
        LocalDateTime now = LocalDateTime.now();
        String accessTokenHash = encryptionService.generateTokenHash(accessTokenId);

        TokenInfo accessTokenInfo = TokenInfo.builder()
                .userUuid(user.getUuid())
                .clientId(clientId)
                .scope(scope)
                .tokenHash(accessTokenId)
                .role(user.getRole())
                .createdAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpiration))
                .lastAccessedAt(now)
                .accessCount(0)
                .isValid(true)
                .build();

        tokenCacheService.cacheAccessToken(clientId, accessTokenHash, accessTokenInfo);

        TokenInfo refreshTokenInfo = TokenInfo.builder()
                .userUuid(user.getUuid())
                .clientId(clientId)
                .scope(scope)
                .tokenHash(refreshTokenHash)
                .role(user.getRole())
                .createdAt(now)
                .expiresAt(now.plusSeconds(refreshTokenExpiration))
                .lastAccessedAt(now)
                .accessCount(0)
                .isValid(true)
                .build();

        tokenCacheService.cacheRefreshToken(clientId, refreshTokenHash, refreshTokenInfo);
    }

    private void cacheNewAccessToken(String clientId, String newAccessToken, User user, String scope) {
        String accessTokenId = extractTokenId(newAccessToken);
        String accessTokenHash = encryptionService.generateTokenHash(accessTokenId);

        LocalDateTime now = LocalDateTime.now();
        TokenInfo accessTokenInfo = TokenInfo.builder()
                .userUuid(user.getUuid())
                .clientId(clientId)
                .scope(scope)
                .tokenHash(accessTokenId)
                .role(user.getRole())
                .createdAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpiration))
                .lastAccessedAt(now)
                .accessCount(0)
                .isValid(true)
                .build();

        tokenCacheService.cacheAccessToken(clientId, accessTokenHash, accessTokenInfo);
    }

    private void cacheRefreshTokenFromEntity(String clientId, RefreshToken refreshToken, User user) {
        LocalDateTime lastAccessed = refreshToken.getLastUsedAt() != null ?
                refreshToken.getLastUsedAt() : LocalDateTime.now();

        TokenInfo refreshTokenInfo = TokenInfo.builder()
                .userUuid(refreshToken.getUserUuid())
                .clientId(clientId)
                .scope(refreshToken.getScope())
                .tokenHash(refreshToken.getTokenHash())
                .role(user.getRole())
                .deviceId(refreshToken.getDeviceId())
                .createdAt(refreshToken.getCreatedAt())
                .expiresAt(refreshToken.getExpiresAt())
                .lastAccessedAt(lastAccessed)
                .accessCount(0)
                .isValid(!Boolean.TRUE.equals(refreshToken.getIsRevoked()))
                .build();

        tokenCacheService.cacheRefreshToken(clientId, refreshToken.getTokenHash(), refreshTokenInfo);
    }

    private Claims parseClaims(String accessToken) {
        return Jwts.parserBuilder()
                .setSigningKey(getJwtSigningKey())
                .build()
                .parseClaimsJws(accessToken)
                .getBody();
    }

    private String extractTokenId(String accessToken) {
        try {
            Claims claims = parseClaims(accessToken);

            String tokenId = claims.get("tokenId", String.class);
            if (tokenId != null) {
                return tokenId;
            }

            tokenId = claims.getId();
            if (tokenId != null) {
                return tokenId;
            }

            String subject = claims.getSubject();
            Date issuedAt = claims.getIssuedAt();
            if (subject != null && issuedAt != null) {
                return subject + "_" + issuedAt.getTime();
            }

            log.warn("OAuth2访问令牌未包含tokenId, 使用回退策略");
            return accessToken.substring(0, Math.min(accessToken.length(), 32));

        } catch (Exception e) {
            log.error("OAuth2访问令牌解析tokenId失败, 使用回退策略", e);
            return accessToken.substring(0, Math.min(accessToken.length(), 32));
        }
    }

    private OauthTokenResponse buildTokenResponse(String accessToken, String refreshToken) {
        return OauthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn((long) accessTokenExpiration)
                .build();
    }
}

