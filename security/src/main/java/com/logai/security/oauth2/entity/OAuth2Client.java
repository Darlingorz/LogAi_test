package com.logai.security.oauth2.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("oauth2_client")
public class OAuth2Client {

    @Id
    private Long id;

    @TableField("client_id")
    private String clientId;

    @TableField("client_secret")
    private String clientSecret;

    @Transient
    private String plainClientSecret;

    @TableField("client_name")
    private String clientName;

    @TableField("description")
    private String description;

    @TableField("redirect_uris")
    private List<String> redirectUris;

    @TableField("grant_types")
    private List<String> grantTypes;

    @TableField("response_types")
    private List<String> responseTypes;

    @TableField("scope")
    private List<String> scope;

    @TableField("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    @TableField("client_uri")
    private String clientUri;

    @TableField("logo_uri")
    private String logoUri;

    @TableField("jwks_uri")
    private String jwksUri;

    @TableField("contacts")
    private List<String> contacts;

    @TableField("enabled")
    private boolean enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
}
