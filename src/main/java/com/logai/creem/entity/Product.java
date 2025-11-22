package com.logai.creem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Product 实体类，用于映射数据库中的 'products' 表。
 * This entity class maps to the 'products' table in the database.
 */
@Data
@TableName("products")
public class Product {

    @TableId
    private Long id;

    @TableField("product_id")
    private String productId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    // 价格，以最小货币单位存储 (例如: 美分)
    @TableField("price")
    private Double price;

    @TableField("currency")
    private String currency;

    @TableField("billing_type")
    private String billingType;

    @TableField("billing_period")
    private String billingPeriod;

    @TableField("status")
    private String status;

    @TableField("tax_mode")
    private String taxMode;

    @TableField("tax_category")
    private String taxCategory;

    @TableField("product_url")
    private String productUrl;

    @TableField("mode")
    private String mode;

    @TableField("default_success_url")
    private String defaultSuccessUrl;

    @TableField("image_url")
    private String imageUrl;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
