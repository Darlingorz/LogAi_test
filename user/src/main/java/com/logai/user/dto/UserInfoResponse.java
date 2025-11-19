package com.logai.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoResponse {
    @JsonProperty("username")
    private String username;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("email")
    private String email;

    @JsonProperty("membership_name")
    private String membershipName;

    @JsonProperty("membership_status")
    private String membershipStatus;

    @JsonProperty("subscription_id")
    private String subscriptionId;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("featurebase_user_hash")
    private String featurebaseUserHash;

    @JsonProperty("not_done_free_trail")
    private Boolean notDoneFreeTrail;
}
