package com.logai.creem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 创建支付会话的响应对象。
 */
@Data
public class CreateCheckoutResponse {

    /**
     * Creem 返回的会话 ID。
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 支付会话的 URL，用于跳转到 Creem 托管的收银台。
     */
    @JsonProperty("checkout_url")
    private String checkoutUrl;

    /**
     * 订单或会话状态。
     */
    @JsonProperty("status")
    private String status;

    /**
     * 支付成功后的跳转 URL。
     */
    @JsonProperty("success_url")
    private String successUrl;

}
