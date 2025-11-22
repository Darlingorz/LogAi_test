package com.logai.oauth2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthorizationRequest {
    private String responseType;
    private String clientId;
    private String redirectUri;
    private String scope;
    private String state;
    private String codeChallenge;
    private String codeChallengeMethod;
    private String userUuid;
    private Long userId;
}