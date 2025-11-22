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
@TableName("user_record")
public class UserRecord {
    @TableId("id")
    private Long id;

    @TableField("chat_id")
    private Long chatId;

    @TableField("theme_id")
    private Long themeId;

    @TableField("user_id")
    private Long userId;

    @TableField("record_date")
    private LocalDateTime recordDate;

    @TableField("event_date")
    private LocalDateTime eventDate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<UserRecordDetail> recordDetails;
}