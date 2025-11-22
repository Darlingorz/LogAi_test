package com.logai.creem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("memberships")
public class Membership {
    @TableId("id")
    private Long id;

    @TableField("name")
    private String name; // 基础版 / 专业版 / 高级版

    @TableField("description")
    private String description; // 描述信息

    @TableField("product_id")
    private Long productId; // 关联 creem.products.id

    @TableField("duration_months")
    private Integer durationMonths; // 订阅时长（单位：月）

    @TableField("role_name")
    private String roleName; // ROLE_MEMBER, ROLE_PREMIUM_MEMBER, ROLE_VIP

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Product product;
}
