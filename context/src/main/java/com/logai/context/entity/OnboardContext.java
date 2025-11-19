package com.logai.context.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("onboard_context")
public class OnboardContext {

    @Id
    @TableField("id")
    private Long id;

    @TableField("theme_id")
    private Long themeId;

    @TableField("context")
    private String context;

    @TableField(exist = false)
    private String themeName;
}