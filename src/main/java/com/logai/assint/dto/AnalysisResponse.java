package com.logai.assint.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 分析结果响应
 */
@Data
public class AnalysisResponse {
    private String description; // 分析描述
    private List<Map<String, Object>> data; // 查询结果数据
    private List<AnalysisField> schema; // 字段定义
}