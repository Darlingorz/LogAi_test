package com.logai.security.oauth2.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClientUpdateRequest {

    private String clientName;

    private String description;

    private List<String> redirectUris;

    private List<String> grantTypes;

    private List<String> responseTypes;

    private List<String> scope;

    private String tokenEndpointAuthMethod;

    private String clientUri;

    private String logoUri;

    private Boolean enabled;
}
