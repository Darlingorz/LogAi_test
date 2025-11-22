package com.logai.assint.entity;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("themes")
public class Theme {
    @TableId("id")
    private Long id;

    @TableField("theme_name")
    private String themeName;

    @TableField("description")
    private String description;

    @TableField("user_id")
    private Long userId;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("status")
    private Integer status = 0;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<Attribute> attributes;
}