package com.logai.oauth2.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oauth2_authorization_code")
public class AuthorizationCode {
    @TableId
    private Long id;

    @TableField("code")
    private String code;

    @TableField("client_id")
    private String clientId;

    @TableField("user_uuid")
    private String userUuid;

    @TableField("user_id")
    private Long userId;

    @TableField("scope")
    private String scope;

    @TableField("redirect_uri")
    private String redirectUri;

    @TableField("code_challenge")
    private String codeChallenge;

    @TableField("code_challenge_method")
    private String codeChallengeMethod;

    @TableField("state")
    private String state;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("used")
    private boolean used;

    @TableField("used_time")
    private LocalDateTime usedTime;
}