package com.logai.creem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.logai.creem.dto.CreateCheckoutRequest;
import com.logai.creem.dto.CreateCheckoutResponse;
import com.logai.creem.dto.OrderStatusResponse;
import com.logai.user.entity.User;

public interface PaymentService {

    /**
     * 创建一个 Creem 支付会话。
     *
     * @param request 支付请求参数
     * @return Creem 返回的会话信息
     */
    CreateCheckoutResponse createCheckoutSession(CreateCheckoutRequest request, User user) throws JsonProcessingException;

    /**
     * 查询订单状态，若未支付则调用 Creem 接口核验并更新本地状态
     */
    OrderStatusResponse getOrderStatusWithVerification(String requestId, String checkoutId);
}
