package com.logai.assint.dto;

import lombok.Data;

import java.util.List;

/**
 * 分析主题识别响应
 */
@Data
public class AnalysisThemeResponse {
    private List<String> themes; // 需要分析的主题列表
    private String analysisType; // 分析类型
}