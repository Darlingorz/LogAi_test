package com.logai.creem.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.logai.creem.dto.UpgradeSubscriptionRequest;
import com.logai.creem.entity.Product;
import com.logai.creem.entity.UserMembership;
import com.logai.creem.mapper.ProductMapper;
import com.logai.creem.mapper.UserMembershipMapper;
import com.logai.creem.service.SubscriptionService;
import com.logai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final UserMembershipMapper userMembershipRepository;
    private final ProductMapper productRepository;

    @Value("${creem.api-key}")
    private String apiKey;

    @Value("${creem.base-url}")
    private String baseUrl;

    @Value("${creem.upgrade-subscription-path}")
    private String upgradeSubscriptionPath;

    @Value("${creem.cancel-subscription-path}")
    private String cancelSubscriptionPath;

    @Override
    public JSONObject upgradeSubscription(String subscriptionId, UpgradeSubscriptionRequest request, User user) {
        if (user == null) {
            throw new IllegalStateException("Unauthenticated users cannot upgrade subscriptions"); // 未登录用户无法升级订阅
        }
        if (StringUtils.isBlank(subscriptionId)) {
            throw new IllegalArgumentException("Subscription ID must not be blank"); // 订阅 ID 不能为空
        }
        UserMembership userMembership = userMembershipRepository.findBySubscriptionId(subscriptionId);
        if (userMembership == null) {
            throw new IllegalArgumentException("Subscription record not found"); // 未找到对应的订阅记录
        }
        if (!Objects.equals(userMembership.getUserId(), user.getId())) {
            throw new IllegalArgumentException("Subscription does not belong to the current user"); // 订阅不属于当前用户
        }

        if (request == null || StringUtils.isBlank(request.getProductId())) {
            throw new IllegalArgumentException("Product ID must not be blank"); // 产品 ID 不能为空
        }
        Product product = productRepository.findByProductId(request.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("Unconfigured product ID: " + request.getProductId());
        }
        JSONObject response = callUpgradeApi(subscriptionId, request);
        log.info("Creem 订阅 {} 升级成功，响应={}", subscriptionId, JSON.toJSONString(response));
        return response;
    }

    @Override
    public JSONObject cancelSubscription(String subscriptionId, User user) {
        if (user == null) {
            throw new IllegalStateException("Unauthenticated users cannot cancel subscriptions"); // 未登录用户无法取消订阅
        }
        if (StringUtils.isBlank(subscriptionId)) {
            throw new IllegalArgumentException("Subscription ID must not be blank"); // 订阅 ID 不能为空
        }
        UserMembership userMembership = userMembershipRepository.findBySubscriptionId(subscriptionId);
        if (userMembership == null) {
            throw new IllegalArgumentException("Subscription record not found"); // 未找到对应的订阅记录
        }
        if (!Objects.equals(userMembership.getUserId(), user.getId())) {
            throw new IllegalArgumentException("Subscription does not belong to the current user"); // 订阅不属于当前用户
        }
        JSONObject response = callCancelApi(subscriptionId);
        log.info("Creem 订阅 {} 取消成功，响应={}", subscriptionId, JSON.toJSONString(response));
        return response;
    }

    private JSONObject callCancelApi(String subscriptionId) {


        String path = cancelSubscriptionPath.replace("{id}", subscriptionId);
        String url = baseUrl + path;

        log.info("请求 Creem 取消订阅: subscriptionId={}, path={}", subscriptionId, path);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .execute();

            int status = response.getStatus();
            String body = response.body();

            if (status >= 400) {
                log.error("取消订阅失败，status={}，response={}", status, body);
                throw new RuntimeException("Creem cancel subscription API call failed: " + status);
            }

            return JSON.parseObject(body);

        } catch (Exception e) {
            log.error("调用 Creem 取消接口异常: {}", e.getMessage(), e);
            throw new RuntimeException("Creem cancel subscription API call failed", e);
        }
    }

    private JSONObject callUpgradeApi(String subscriptionId, UpgradeSubscriptionRequest request) {
        String path = upgradeSubscriptionPath.replace("{id}", subscriptionId);
        String url = baseUrl + path;

        log.info("请求 Creem 升级订阅: subscriptionId={}, path={}, body={}",
                subscriptionId, path, JSON.toJSONString(request));

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body(JSON.toJSONString(request))
                    .execute();

            int status = response.getStatus();
            String body = response.body();

            // 模拟 WebClient.onStatus 行为
            if (status >= 400) {
                log.error("升级订阅失败，status={}，response={}", status, body);
                throw new RuntimeException("Creem upgrade subscription API call failed: " + status);
            }

            return JSON.parseObject(body);

        } catch (Exception e) {
            log.error("调用 Creem 升级接口异常: {}", e.getMessage(), e);
            throw new RuntimeException("Creem upgrade subscription API call failed", e);
        }
    }
}