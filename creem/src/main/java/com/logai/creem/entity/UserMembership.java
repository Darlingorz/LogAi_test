package com.logai.creem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_memberships")
public class UserMembership {
    @TableId("id")
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("membership_id")
    private Long membershipId;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("status")
    private Status status;

    @TableField("subscription_id")
    private String subscriptionId;

    @TableField("last_transaction_id")
    private String lastTransactionId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE("active"), CANCELED("canceled"), UNPAID("unpaid"), PAUSED("paused"), TRIALING("trialing"), UNKNOWN("unknown");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static Status fromValue(String value) {
            for (Status status : values()) {
                if (status.value.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            return UNKNOWN;
        }
    }
}
