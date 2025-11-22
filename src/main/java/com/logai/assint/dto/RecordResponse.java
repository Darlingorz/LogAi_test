package com.logai.assint.dto;

import com.logai.assint.enums.DataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用于存储属性信息的DTO类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordResponse {
    private String themeName;
    private List<List<AttrebuteValue>> attrebuteValue;

    @Data
    public static class AttrebuteValue {
        private String attributeName;
        private DataType dataType;
        private String value;
        private String unit;
    }
}