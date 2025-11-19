package com.logai.creem.dto.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionObject {
    private Product product;

    private Customer customer;

    private String status;

    private Map<String, Object> metadata;

    @JsonProperty("collection_method")
    private String collectionMethod;

    @JsonProperty("last_transaction_id")
    private String lastTransactionId;

    @JsonProperty("last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @JsonProperty("next_transaction_date")
    private LocalDateTime nextTransactionDate;

    @JsonProperty("current_period_start_date")
    private LocalDateTime currentPeriodStartDate;

    @JsonProperty("current_period_end_date")
    private LocalDateTime currentPeriodEndDate;

    @JsonProperty("canceled_at")
    private LocalDateTime canceledAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("mode")
    private String mode;


}
