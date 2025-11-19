package com.logai.creem.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CreemObjectType {
    CHECKOUT("checkout"),
    SUBSCRIPTION("subscription"),
    REFUND("refund"),
    DISPUTE("dispute"),
    UNKNOWN("unknown");

    private final String value;

    CreemObjectType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CreemObjectType fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (CreemObjectType type : CreemObjectType.values()) {
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