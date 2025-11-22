package com.logai.creem.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    // 已支付
    COMPLETED("completed"),
    // 待支付
    PENDING("pending"),
    // 处理中
    PROCESSING("processing"),
    // 未知状态
    UNKNOWN("unknown");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OrderStatus fromValue(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
