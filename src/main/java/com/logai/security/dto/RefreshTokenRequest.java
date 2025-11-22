package com.logai.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Refresh Token请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    private String refreshToken;

    /**
     * 设备ID，用于设备绑定验证
     */
    private String deviceId;

    /**
     * 是否同时获取新的Refresh Token（轮换）
     */
    private boolean rotateRefreshToken = false;

    /**
     * 用户代理信息，用于安全记录
     */
    private String userAgent;

    /**
     * IP地址，用于安全记录
     */
    private String ipAddress;
}