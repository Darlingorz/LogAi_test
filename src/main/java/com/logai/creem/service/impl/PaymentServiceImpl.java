package com.logai.creem.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.logai.common.utils.GenerateRandomUtil;
import com.logai.creem.dto.CreateCheckoutRequest;
import com.logai.creem.dto.CreateCheckoutResponse;
import com.logai.creem.dto.OrderStatusResponse;
import com.logai.creem.entity.Order;
import com.logai.creem.entity.Product;
import com.logai.creem.enums.OrderStatus;
import com.logai.creem.mapper.OrderMapper;
import com.logai.creem.mapper.ProductMapper;
import com.logai.creem.service.PaymentService;
import com.logai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final GenerateRandomUtil randomUtil;
    private final OrderMapper orderMapper;
    private final ProductMapper productRepository;

    @Value("${creem.api-key}")
    private String apiKey;

    @Value("${creem.base-url}")
    private String baseUrl;

    @Value("${creem.create-checkout-path}")
    private String checkoutPath;

    @Override
    public CreateCheckoutResponse createCheckoutSession(CreateCheckoutRequest request, User user) {
        Long userId = null;
        if (user != null) {
            userId = user.getId();
            CreateCheckoutRequest.Customer customer = CreateCheckoutRequest.Customer
                    .builder()
                    .email(user.getEmail())
                    .build();
            request.setCustomer(customer);
        }
        // 设置 customer

        String requestId = randomUtil.generateId("creem_");
        request.setRequestId(requestId);

        log.debug("请求 Creem 创建支付会话: path={}, request={}", checkoutPath, JSON.toJSONString(request));

        // ======== 1. 查询产品 ========
        Product product = productRepository.findByProductId(request.getProductId());
        if (product == null) {
            throw new RuntimeException("Creem 产品不存在");
        }

        // ======== 2. 使用 Hutool 发送一次 POST 请求 ========
        String url = baseUrl + checkoutPath;

        HttpResponse response;
        try {
            response = HttpRequest.post(url)
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(request))
                    .execute();
        } catch (Exception e) {
            log.error("调用 Creem 创建 Checkout 接口失败: {}", e.getMessage(), e);
            throw new RuntimeException("Creem 支付接口调用失败");
        }

        // 此处 body 已经拿到了，不会二次请求
        String responseBody = response.body();

        // ======== 3. 模拟 WebClient.onStatus 行为 ========
        if (response.getStatus() >= 400) {
            log.error("创建 Creem 支付会话失败，状态码：{}，响应：{}", response.getStatus(), responseBody);
            throw new RuntimeException("Creem 支付接口调用失败: " + response.getStatus());
        }

        // ======== 4. JSON 解析 ========
        JSONObject jsonObject = JSON.parseObject(responseBody);

        // ======== 5. 保存订单 ========
        Order order = formatOrder(jsonObject, requestId, userId, product.getId());
        orderMapper.insert(order);

        log.info("订单已成功保存: {}", order.getRequestId());

        // ======== 6. 返回结果 ========
        CreateCheckoutResponse resp = new CreateCheckoutResponse();
        resp.setSessionId(jsonObject.getString("id"));
        resp.setCheckoutUrl(jsonObject.getString("checkout_url"));
        resp.setStatus(jsonObject.getString("status"));
        resp.setSuccessUrl(jsonObject.getString("success_url"));

        return resp;
    }

    @Override
    public OrderStatusResponse getOrderStatusWithVerification(String requestId, String checkoutId) {
        // 1. 查询本地订单（同步）
        Order order = orderMapper.selectById(requestId);

        // ============================
        // Case A: 本地找到订单
        // ============================
        if (order != null) {

            // A1. 本地订单已完成（与 reactive 一致：直接返回，不请求远端）
            if (OrderStatus.COMPLETED.equals(order.getStatus())) {
                return new OrderStatusResponse(order.getStatus(), order.getUpdatedAt());
            }

            // A2. 本地未完成 → 调 Creem 查询远端状态
            String remoteStatus = queryCreemCheckout(checkoutId);

            // A2-1. 远端已完成 → 更新本地订单
            if (OrderStatus.COMPLETED.getValue().equalsIgnoreCase(remoteStatus)) {
                order.setStatus(OrderStatus.COMPLETED);
                order.setUpdatedAt(LocalDateTime.now());
                orderMapper.insertOrUpdate(order);

                return new OrderStatusResponse(order.getStatus(), order.getUpdatedAt());
            }

            // A2-2. 远端未完成 → 返回本地状态
            return new OrderStatusResponse(order.getStatus(), order.getUpdatedAt());
        }

        // ============================
        // Case B: 本地没有订单 (switchIfEmpty 分支)
        // ============================

        // 直接调 creem 查询
        String remoteStatus = queryCreemCheckout(checkoutId);

        // 远端返回的 status 是字符串，需要转成枚举
        try {
            return new OrderStatusResponse(
                    OrderStatus.valueOf(remoteStatus.toUpperCase()),
                    null
            );
        } catch (Exception e) {
            // 如果远端返回 unknown 或无效值
            return new OrderStatusResponse(OrderStatus.UNKNOWN, null);
        }
    }

    /**
     * 调用 Creem Get Checkout API 查询状态
     */
    private String queryCreemCheckout(String checkoutId) {
        String url = String.format(checkoutPath + "?checkout_id=%s", checkoutId);

        try {
            // 发送 HTTP 请求
            String responseStr = HttpRequest.get(baseUrl + url)
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .execute()
                    .body();

            if (StringUtils.isEmpty(responseStr)) {
                log.error("Creem Get Checkout 返回空响应，checkoutId={}", checkoutId);
                return "unknown";
            }

            JSONObject json = JSON.parseObject(responseStr);
            String status = json.getString("status");

            log.info("从 Creem 查询到 checkoutId={} 状态={}", checkoutId, status);
            return status;

        } catch (Exception e) {
            log.error("调用 Creem Get Checkout 接口失败: {}", e.getMessage(), e);
            return "unknown";
        }
    }

    /**
     * 格式化成Order实体类
     */
    private Order formatOrder(JSONObject jsonObject, String requestId, Long userId, Long productId) {
        Order order = new Order();
        order.setRequestId(requestId);
        order.setCheckoutId(jsonObject.getString("id"));
        order.setProductId(productId);
        order.setUserId(userId);
        order.setUnits(jsonObject.getInteger("units"));
        order.setStatus(OrderStatus.fromValue(jsonObject.getString("status")));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
