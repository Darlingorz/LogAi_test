package com.logai.creem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代表会员资格特性的实体类
 */
@Data
@TableName("membership_features")
public class MembershipFeature {

    /**
     * 主键ID
     */
    @TableId("id")
    private Long id;

    /**
     * 关联的会员ID
     */
    @TableField("membership_id")
    private Long membershipId;

    /**
     * 特性键，用于标识具体的功能
     */
    @TableField("feature_key")
    private String featureKey;

    /**
     * 每日使用限制
     */
    @TableField("daily_limit")
    private Integer dailyLimit;

    /**
     * 每月使用限制
     */
    @TableField("monthly_limit")
    private Integer monthlyLimit;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}