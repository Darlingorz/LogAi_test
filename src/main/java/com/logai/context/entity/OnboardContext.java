package com.logai.context.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("onboard_context")
public class OnboardContext {

    @TableId
    private Long id;

    @TableField("theme_id")
    private Long themeId;

    @TableField("context")
    private String context;

    @TableField(exist = false)
    private String themeName;
}