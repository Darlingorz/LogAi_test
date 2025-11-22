package com.logai.context.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ContextType {
    COMMON_INFO("common_info"),
    TEMP_INFO("temp_info"),
    THEME_CONTEXT_INFO("theme_context_info"),
    WORKFLOW("workflow"),
    UNKNOWN("unknown");

    private final String value;

    ContextType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }


    @JsonCreator
    public static ContextType fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ContextType type : ContextType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

}
