package com.logai.creem.dto.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.logai.creem.enums.OrderStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Creem Webhook 中 checkout 对象的完整结构
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckoutObject {
    @JsonProperty("request_id")
    private String requestId;

    private Order order;

    private Product product;

    private Customer customer;

    private Subscription subscription;

    @JsonProperty("custom_fields")
    private List<Object> customFields;

    private OrderStatus status;

    private Integer units;
    
    private Map<String, Object> metadata;

}
