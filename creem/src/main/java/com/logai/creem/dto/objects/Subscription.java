package com.logai.creem.dto.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Subscription {
    private String id;
    private String object;
    private String product;
    private String customer;

    @JsonProperty("collection_method")
    private String collectionMethod;

    private String status;

    @JsonProperty("canceled_at")
    private LocalDateTime canceledAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    private Map<String, Object> metadata;

    private String mode;
}