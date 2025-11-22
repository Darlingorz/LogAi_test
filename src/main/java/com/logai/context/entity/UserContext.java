package com.logai.context.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.logai.context.enums.ContextType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_context")
public class UserContext {

    @TableId
    private Long id;

    @TableField("info")
    private String info;

    @TableField("context_type")
    private ContextType contextType;

    @TableField("user_id")
    private Long userId;

    @TableField("theme_id")
    private Long themeId;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String themeName;
}
