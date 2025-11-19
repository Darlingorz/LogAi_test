package com.logai.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId
    private Long id;

    private String username;

    private String password;

    private String email;

    private String phone;

    private String status;

    @TableField("time_zone")
    private String timeZone;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @TableField("featurebase_user_hash")
    private String featurebaseUserHash;

    private String uuid;

    // ===== 非数据库字段 =====

    @TableField(exist = false)
    private Integer role;

    @TableField(exist = false)
    private String accessToken;

    @TableField(exist = false)
    private String refreshToken;

    @TableField(exist = false)
    private String avatar;
}
