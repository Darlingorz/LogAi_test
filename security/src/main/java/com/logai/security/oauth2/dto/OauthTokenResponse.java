package com.logai.security.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class OauthTokenResponse {

    /**
     * 访问令牌
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * 刷新令牌
     */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 令牌类型
     */
    @JsonProperty("token_type")
    private String tokenType = "Bearer";

    /**
     * 令牌有效期
     */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /**
     * 错误码
     */
    @JsonProperty("error")
    private String error;

    /**
     * 错误描述
     */
    @JsonProperty("error_description")
    private String errorDescription;
}