package com.logai.assint.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IntentType {
    RECORD("record"),
    ANALYZE("analyze"),
    CHAT("chat"),
    ILLEGAL("illegal");

    private final String value;

    IntentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static IntentType fromValue(String value) {
        for (IntentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return ILLEGAL;
    }
}
