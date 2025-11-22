package com.logai.security.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refresh Token实体
 * 存储加密的Refresh Token信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("refresh_tokens")
public class RefreshToken {

    @TableId
    private Long id;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("encrypted_token")
    private String encryptedToken;

    @TableField("user_uuid")
    private String userUuid;

    @TableField("user_id")
    @JsonIgnore
    private Long userId;

    @TableField("client_id")
    private String clientId;

    @TableField("scope")
    private String scope;

    @TableField("device_id")
    private String deviceId;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField("is_revoked")
    private Boolean isRevoked;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("revoke_reason")
    private String revokeReason;

    /**
     * 检查Token是否有效
     */
    public boolean isValid() {
        return !isRevoked && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * 检查Token是否即将过期（24小时内）
     */
    public boolean isExpiringSoon() {
        if (expiresAt == null) {
            return false;
        }
        LocalDateTime soon = LocalDateTime.now().plusHours(24);
        return expiresAt.isBefore(soon);
    }

    /**
     * 更新最后使用时间
     */
    public void updateLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * 废除Token
     */
    public void revoke(String reason) {
        this.isRevoked = true;
        this.revokedAt = LocalDateTime.now();
        this.revokeReason = reason;
    }
}