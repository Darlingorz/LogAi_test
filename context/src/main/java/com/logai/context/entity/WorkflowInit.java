package com.logai.context.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("workflow_init")
public class WorkflowInit {
    @TableField
    private Long id;
    @TableField("theme_id")
    private String themeId;
    @TableField("info")
    private String info;
}
