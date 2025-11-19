package com.logai.creem.service;

import com.alibaba.fastjson.JSONObject;
import com.logai.creem.dto.UpgradeSubscriptionRequest;
import com.logai.user.entity.User;

public interface SubscriptionService {

    /**
     * 调用 Creem 升级订阅接口。
     *
     * @param subscriptionId Creem 订阅 ID
     * @param request        升级参数
     * @param user           当前登录用户
     * @return Creem 返回的订阅对象信息
     */
    JSONObject upgradeSubscription(String subscriptionId, UpgradeSubscriptionRequest request, User user);

    /**
     * 调用 Creem 取消订阅接口。
     *
     * @param subscriptionId Creem 订阅 ID
     * @param user           当前登录用户
     * @return Creem 返回的取消订阅结果
     */
    JSONObject cancelSubscription(String subscriptionId, User user);
}