package com.logai.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("social_accounts")
public class SocialAccount {
    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("provider")
    private String provider;

    @TableField("provider_user_id")
    private String providerUserId;

    @TableField("email")
    private String email;

    @TableField("name")
    private String name;

    @TableField("picture")
    private String picture;

    @TableField("access_token")
    private String accessToken;

    @TableField("refresh_token")
    private String refreshToken;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}