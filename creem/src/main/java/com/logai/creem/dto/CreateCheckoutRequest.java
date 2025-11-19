package com.logai.creem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建支付会话的请求参数。
 */
@Data
public class CreateCheckoutRequest {
    /**
     * 结账请求ID，用于标识本次请求。
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * Creem 产品 ID。
     */
    @JsonProperty("product_id")
    private String productId;

    /**
     * 购买数量，默认为 1。
     */
    @JsonProperty("units")
    private Integer units = 1;

    /**
     * 折扣码。
     */
    @JsonProperty("discount_code")
    private String discountCode;
    /**
     * 客户。
     */

    @JsonProperty("customer")
    private Customer customer;

    /**
     * 使用自定义字段从客户那里收集其他信息。
     */
    @JsonProperty("custom_field")
    private List<CustomField> customField;


    /**
     * 支付成功后跳转地址。
     */
    @JsonProperty("success_url")
    private String successUrl;

    /**
     * 附加的元数据，将透传给 Creem。
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;


    @Data
    @Builder
    public static class Customer {

        @JsonProperty("email")
        private String email;

        @JsonProperty("id")
        private String id;
    }

    @Data
    public static class CustomField {
        @JsonProperty("type")
        private String type;

        @JsonProperty("key")
        private String key;

        @JsonProperty("label")
        private String label;

        @JsonProperty("optional")
        private boolean optional;

        @JsonProperty("text")
        private Text text;

        @Data
        public static class Text {

            @JsonProperty("max_length")
            private Integer maxLength;

            @JsonProperty("min_length")
            private Integer minLength;
        }

    }
}
