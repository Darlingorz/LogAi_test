package com.logai.assint.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.logai.assint.enums.DataType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("attributes")
public class Attribute {

    @TableId("id")
    private Long id;

    @TableField("theme_id")
    private Long themeId;

    @TableField("user_id")
    private Long userId;

    @TableField("attribute_name")
    private String attributeName;

    @TableField("data_type")
    private DataType dataType;

    @TableField("description")
    private String description;

    @TableField("display_order")
    private Integer displayOrder = 0;

    @TableField("status")
    private Integer status = 0;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}