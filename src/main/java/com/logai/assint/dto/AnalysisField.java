package com.logai.assint.dto;

import lombok.Data;

import java.util.List;

/**
 * 分析字段定义
 */
@Data
public class AnalysisField {
    private String key;
    private String des;
    private String type;
    private List<AnalysisField> children;
}