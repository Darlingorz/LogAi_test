package com.logai.security.oauth2.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccessToken {
    private String id;
    private String token;
    private String userUuid;
    private String clientId;
    private String scope;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
}