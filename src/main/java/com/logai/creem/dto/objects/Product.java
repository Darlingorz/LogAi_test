package com.logai.creem.dto.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {
    private String id;
    private String name;
    private String description;

    @JsonProperty("image_url")
    private String imageUrl;

    private Integer price;
    private String currency;

    @JsonProperty("billing_type")
    private String billingType;

    @JsonProperty("billing_period")
    private String billingPeriod;

    private String status;

    @JsonProperty("tax_mode")
    private String taxMode;

    @JsonProperty("tax_category")
    private String taxCategory;

    @JsonProperty("default_success_url")
    private String defaultSuccessUrl;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    private String mode;
}