package com.logai.assint.dto;

import lombok.Data;

import java.util.List;

@Data
public class ManualRecordResponse {
    private Long chatId;
    private String themeName;
    private List<ManualRecordEntry> records;

    @Data
    public static class ManualRecordEntry {
        private List<AttributeValue> attributes;
        private String eventTime;
    }

    @Data
    public static class AttributeValue {
        private String attributeName;
        private String value;
        private String unit;
        private String dataType;
        private String groupId;
    }
}