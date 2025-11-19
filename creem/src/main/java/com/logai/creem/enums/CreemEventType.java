package com.logai.creem.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CreemEventType {
    //结帐完成
    CHECKOUT_COMPLETED("checkout.completed"),
    //订阅活跃
    SUBSCRIPTION_ACTIVE("subscription.active"),
    //订阅支付成功
    SUBSCRIPTION_PAID("subscription.paid"),
    //订阅取消
    SUBSCRIPTION_CANCELED("subscription.canceled"),
    //订阅过期
    SUBSCRIPTION_EXPIRED("subscription.expired"),
    //订阅更新
    SUBSCRIPTION_UPDATE("subscription.update"),
    //订阅试用
    SUBSCRIPTION_TRIALING("subscription.trialing"),
    //订阅暂停
    SUBSCRIPTION_PAUSED("subscription.paused"),
    //退款创建
    REFUND_CREATED("refund.created"),
    //争议创建
    DISPUTE_CREATED("dispute.created"),
    //争议关闭
    DISPUTE_CLOSED("dispute.closed"),

    UNKNOWN("unknown");

    private final String value;

    CreemEventType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CreemEventType fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (CreemEventType type : CreemEventType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public String getValue() {
        return value;
    }
}