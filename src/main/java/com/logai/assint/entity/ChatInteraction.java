package com.logai.assint.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_interaction")
public class ChatInteraction {
    @TableId("id")
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_input")
    private String userInput;

    @TableField("ai_response")
    private String aiResponse;

    @TableField("intent_type")
    private String intentType;

    @TableField("total_prompt_tokens")
    private Integer totalPromptTokens;

    @TableField("total_completion_tokens")
    private Integer totalCompletionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}