package com.logai.assint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI生成的SQL分析请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisRequest {
    private String description; // 分析描述
    private String sql; // SQL语句
    private List<AnalysisField> schema; // 字段定义
}