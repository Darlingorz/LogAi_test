package com.logai.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;

@Component
public class JwtUtils {

    private final JwtParser jwtParser;

    public JwtUtils(@Value("${token.jwt.secret}") String jwtSecret) {
        // Base64解码密钥
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);

        // 确保密钥长度符合HS512要求（至少512位）
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException("JWT secret key must be at least 512 bits (64 bytes) for HS512 algorithm");
        }

        SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);

        this.jwtParser = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build();
    }

    /**
     * 从JWT令牌中提取用户ID
     */
    public String getUserIdFromToken(String token) {
        return extractClaims(token)
                .map(Claims::getSubject)
                .orElseThrow(() -> new JwtException("Invalid JWT token: subject is missing"));
    }

    /**
     * 从JWT令牌中提取用户角色
     */
    public Integer getUserRoleFromToken(String token) {
        return extractClaims(token)
                .map(claims -> claims.get("role", Integer.class))
                .orElseThrow(() -> new JwtException("Invalid JWT token: role is missing"));
    }

    /**
     * 从JWT令牌中提取OAuth2客户端ID（如果存在）
     */
    public String getClientIdFromToken(String token) {
        return extractClaims(token)
                .map(claims -> claims.get("client_id", String.class))
                .orElse(null);
    }

    /**
     * 验证JWT令牌是否有效
     */

    public boolean validateToken(String token) {
        return extractClaims(token).isPresent();

    }

    /**
     * 提取 JWT 中的 Claims 信息，统一处理解析错误。
     */
    public Optional<Claims> extractClaims(String token) {
        try {
            return Optional.of(jwtParser.parseClaimsJws(token).getBody());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
