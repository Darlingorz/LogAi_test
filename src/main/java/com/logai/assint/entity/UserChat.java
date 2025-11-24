package com.logai.assint.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_chat")
public class UserChat {
    @TableId
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("original_content")
    private String originalContent;

    @TableField("ai_response")
    private String aiResponse;

    @TableField("session_id")
    private String sessionId;

    @TableField("conversation_type")
    private String conversationType;

    @TableField("error_reason")
    private String errorReason;

    @TableField("status")
    private String status;

    @TableField("total_prompt_tokens")
    private Integer totalPromptTokens;

    @TableField("total_completion_tokens")
    private Integer totalCompletionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("record_date")
    private LocalDate recordDate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<UserRecordDetail> attributeValues;

}