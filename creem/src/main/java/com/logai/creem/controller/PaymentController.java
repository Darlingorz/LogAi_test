package com.logai.creem.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.common.model.Result;
import com.logai.creem.dto.CreateCheckoutRequest;
import com.logai.creem.dto.CreemWebhookEvent;
import com.logai.creem.service.CreemWebhookService;
import com.logai.creem.service.PaymentService;
import com.logai.creem.util.CreemSignatureUtil;
import com.logai.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/creem/order")
public class PaymentController {

    private final PaymentService paymentService;
    private final CreemWebhookService creemWebhookService;
    private final ObjectMapper objectMapper;

    @Value("${creem.webhook.secret}")
    private String webhookSecret;

    @Value("${creem.api-key}")
    private String apiKey;


    @PostMapping("/checkout")
    public Result createCheckout(
            @RequestBody CreateCheckoutRequest request,
            @AuthenticationPrincipal User user) throws JsonProcessingException {
        return Result.success(paymentService.createCheckoutSession(request, user));
    }

    @PostMapping("/checkout/status/callback")
    public ResponseEntity<String> checkoutSuccess(HttpServletRequest request) {
        try {
            // 1. 读取原始 body
            String rawBody = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

            // 2. 获取签名头
            String signature = request.getHeader("creem-signature");

            // 3. 验签
            if (signature == null || !CreemSignatureUtil.verifyWebhookSignature(rawBody, webhookSecret, signature)) {
                log.warn("Webhook 验签失败: {}", rawBody);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
            }

            // 4. 解析 JSON
            CreemWebhookEvent event = objectMapper.readValue(rawBody, CreemWebhookEvent.class);

            if (event.getObject() != null) {
                event.getObject().buildTypedObject(objectMapper);
            }

            // 5. 处理事件
            creemWebhookService.handleEvent(event);

            // 6. 返回 success
            return ResponseEntity.ok("ok");

        } catch (Exception e) {
            log.error("Webhook 解析失败", e);
            return ResponseEntity.badRequest().body("invalid payload");
        }
    }

    /**
     * 查询订单状态，若未支付则调用 Creem 接口核验并更新本地状态
     */
    @GetMapping("/status")
    public Result getOrderStatus(
            @RequestParam("request_id") String requestId,
            @RequestParam("checkout_id") String checkoutId,
            @RequestParam("order_id") String orderId,
            @RequestParam("product_id") String productId,
            @RequestParam("customer_id") String customerId,
            @RequestParam("signature") String signature,
            @RequestParam(value = "subscription_id", required = false) String subscriptionId
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("request_id", requestId);
        params.put("checkout_id", checkoutId);
        params.put("order_id", orderId);
        params.put("customer_id", customerId);
        params.put("subscription_id", subscriptionId);
        params.put("product_id", productId);

        boolean verified = CreemSignatureUtil.verifyReturnUrlSignature(params, apiKey, signature);

        if (!verified) {
            log.warn("❌ 支付状态查询接口：签名验证失败 requestId={} checkoutId={}", requestId, checkoutId);
            return Result.failure(403, "签名验证失败");
        }
        return Result.success(paymentService.getOrderStatusWithVerification(requestId, checkoutId));
    }
}
