package com.logai.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token响应DTO
 * 用于返回Access Token和Refresh Token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 访问令牌过期时间（秒）
     */
    private Long accessTokenExpiresIn;

    /**
     * 刷新令牌过期时间（秒）
     */
    private Long refreshTokenExpiresIn;

    /**
     * 令牌类型
     */
    private String tokenType = "Bearer";

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    /**
     * 用户信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String email;
        private String username;
        private Integer role;
        private String avatar;
    }

    /**
     * 快速构建Token响应的静态方法
     */
    public static TokenResponse of(String accessToken, String refreshToken,
                                   Long accessTokenExpiresIn, Long refreshTokenExpiresIn) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .refreshTokenExpiresIn(refreshTokenExpiresIn)
                .build();
    }

    /**
     * 只包含Access Token的响应（刷新时）
     */
    public static TokenResponse accessOnly(String accessToken, Long accessTokenExpiresIn) {
        return TokenResponse.builder()
                .accessToken(accessToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .build();
    }
}