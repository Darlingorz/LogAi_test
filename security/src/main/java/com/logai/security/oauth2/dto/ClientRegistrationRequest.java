package com.logai.security.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Data
public class ClientRegistrationRequest {

    @JsonProperty("client_name")
    @JsonAlias({"clientName"})
    private String clientName;

    @JsonProperty("redirect_uris")
    @JsonAlias({"redirectUris"})
    private List<String> redirectUris = Collections.emptyList();

    @JsonProperty("grant_types")
    @JsonAlias({"grantTypes"})
    private List<String> grantTypes = Collections.emptyList();

    @JsonProperty("response_types")
    @JsonAlias({"responseTypes"})
    private List<String> responseTypes = Collections.emptyList();

    @JsonProperty("scope")
    @JsonAlias({"scopes"})
    private List<String> scope = Collections.emptyList();

    @JsonProperty("token_endpoint_auth_method")
    @JsonAlias({"tokenEndpointAuthMethod"})
    private String tokenEndpointAuthMethod;

    @JsonProperty("client_uri")
    private String clientUri;

    @JsonProperty("logo_uri")
    private String logoUri;

    /**
     * 兼容单个字符串 redirect_uri 写法
     */
    @JsonProperty("redirect_uri")
    public void setRedirectUriSingle(String uri) {
        if (StringUtils.hasText(uri)) {
            this.redirectUris = List.of(uri.trim());
        }
    }

    @JsonProperty("grant_type")
    public void setGrantTypeSingle(String grantType) {
        if (StringUtils.hasText(grantType)) {
            this.grantTypes = List.of(grantType.trim());
        }
    }

    @JsonProperty("response_type")
    public void setResponseTypeSingle(String responseType) {
        if (StringUtils.hasText(responseType)) {
            this.responseTypes = List.of(responseType.trim());
        }
    }
}
