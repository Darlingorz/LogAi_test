package com.logai.creem.dto.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {
    private String id;
    private String customer;
    private String product;
    private Integer amount;
    private String currency;
    private String status; // paid / pending
    private String type; // one_time / recurring

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

}