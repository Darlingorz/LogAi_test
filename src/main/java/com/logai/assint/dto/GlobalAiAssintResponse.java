package com.logai.assint.dto;

import com.logai.assint.enums.IntentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用全局AI助手响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GlobalAiAssintResponse {
    private IntentType intentType;
    private Object data;
}
