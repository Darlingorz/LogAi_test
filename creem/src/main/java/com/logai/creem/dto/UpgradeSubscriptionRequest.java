package com.logai.creem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 请求 Creem 升级订阅接口的参数封装。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpgradeSubscriptionRequest {

    @JsonProperty("product_id")
    private String productId;


    @JsonProperty("update_behavior")
    private String updateBehavior;

}