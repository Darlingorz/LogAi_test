package com.logai.creem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.logai.creem.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId
    private Long id;

    @TableField("request_id")
    @JsonProperty("request_id")
    private String requestId;

    @TableField("checkout_id")
    @JsonProperty("checkout_id")
    private String checkoutId;

    @TableField("order_id")
    @JsonProperty("order_id")
    private String orderId;

    @TableField("customer_id")
    @JsonProperty("customer_id")
    private String customerId;

    @TableField("product_id")
    @JsonProperty("product_id")
    private Long productId;

    @TableField("user_id")
    @JsonProperty("user_id")
    private Long userId;

    @TableField("units")
    @JsonProperty("units")
    private Integer units;

    @TableField("status")
    private OrderStatus status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
