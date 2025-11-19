package com.logai.creem.controller;

import com.alibaba.fastjson.JSONObject;
import com.logai.common.model.Result;
import com.logai.creem.dto.UpgradeSubscriptionRequest;
import com.logai.creem.service.SubscriptionService;
import com.logai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/creem/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/{subscriptionId}/upgrade")
    public Result upgradeSubscription(@PathVariable("subscriptionId") String subscriptionId,
                                      @RequestBody UpgradeSubscriptionRequest request,
                                      @AuthenticationPrincipal User user) {
        try {
            JSONObject jsonObject = subscriptionService.upgradeSubscription(subscriptionId, request, user);
            log.info("升级订阅成功 subscriptionId={}: {}", subscriptionId, jsonObject);
            return Result.success(jsonObject);
        } catch (Exception e) {
            log.error("升级订阅失败 subscriptionId={}: {}", subscriptionId, e.getMessage(), e);
            return Result.failure("升级订阅失败: " + e.getMessage());
        }
    }


    @PostMapping("/{subscriptionId}/cancel")
    public Result cancelSubscription(@PathVariable("subscriptionId") String subscriptionId,
                                     @AuthenticationPrincipal User user) {
        try {
            JSONObject jsonObject = subscriptionService.cancelSubscription(subscriptionId, user);
            log.info("取消订阅成功 subscriptionId={}: {}", subscriptionId, jsonObject);
            return Result.success(jsonObject);
        } catch (Exception e) {
            log.error("取消订阅失败 subscriptionId={}: {}", subscriptionId, e.getMessage(), e);
            return Result.failure("取消订阅失败: " + e.getMessage());
        }
    }
}