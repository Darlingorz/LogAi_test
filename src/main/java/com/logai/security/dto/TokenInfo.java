package com.logai.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token信息缓存对象
 * 用于Redis缓存中的Token信息存储
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {

    /**
     * 用户UUID
     */
    private String userUuid;

    /**
     * OAuth2客户端ID（如适用）
     */
    private String clientId;

    /**
     * OAuth2授权范围（如适用）
     */
    private String scope;

    /**
     * Token哈希值
     */
    private String tokenHash;

    /**
     * 用户角色
     */
    private Integer role;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 访问次数
     */
    private Integer accessCount;

    /**
     * 是否有效
     */
    private Boolean isValid;

    /**
     * 是否过期
     */
    private boolean expired;

    /**
     * 剩余过期时间（秒）
     */
    private Integer remainingTime;

    /**
     * 更新访问信息
     */
    public void updateAccessInfo() {
        this.lastAccessedAt = LocalDateTime.now();
        if (this.accessCount == null) {
            this.accessCount = 0;
        }
        this.accessCount++;
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * 获取剩余过期时间（秒）
     */
    public long getRemainingTime() {
        if (expiresAt == null) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
    }
}