package com.logai.creem.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.logai.creem.dto.CreemWebhookEvent;
import com.logai.creem.dto.objects.CheckoutObject;
import com.logai.creem.dto.objects.SubscriptionObject;
import com.logai.creem.entity.Membership;
import com.logai.creem.entity.Order;
import com.logai.creem.entity.Product;
import com.logai.creem.entity.UserMembership;
import com.logai.creem.enums.CreemEventType;
import com.logai.creem.enums.OrderStatus;
import com.logai.creem.mapper.MembershipMapper;
import com.logai.creem.mapper.OrderMapper;
import com.logai.creem.mapper.ProductMapper;
import com.logai.creem.mapper.UserMembershipMapper;
import com.logai.creem.service.CreemWebhookService;
import com.logai.oauth2.service.OAuth2TokenService;
import com.logai.security.service.TokenService;
import com.logai.user.entity.User;
import com.logai.user.mapper.UserMapper;
import com.logai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreemWebhookServiceImpl implements CreemWebhookService {
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final MembershipMapper membershipMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final TokenService tokenService;
    private final OAuth2TokenService oauth2TokenService;

    @Override
    public void handleEvent(CreemWebhookEvent event) {
        CreemEventType eventType = event.getCreemEventType();
        log.info("收到 Creem Webhook: {}", JSON.toJSONString(event));

        switch (eventType) {
            case CHECKOUT_COMPLETED -> handleCheckoutCompleted(event);
            case SUBSCRIPTION_PAID -> handleSubscriptionPaid(event);
            case SUBSCRIPTION_ACTIVE, SUBSCRIPTION_TRIALING -> handleSubscriptionActiveAndTrialing(event);
            case SUBSCRIPTION_CANCELED, SUBSCRIPTION_EXPIRED -> handleSubscriptionCanceledAndExpired(event);
//            case SUBSCRIPTION_UPDATE -> handleSubscriptionUpdated(event);
            default -> log.info("忽略未处理的事件类型: {}", eventType);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleSubscriptionUpdated(CreemWebhookEvent event) {

        SubscriptionObject subscription = event.getObject().getSubscription();
        if (subscription == null) {
            log.warn("subscription.update 缺少 subscription 对象: {}", JSON.toJSONString(event));
            return;
        }

        String subscriptionId = event.getObject().getId();
        String email = subscription.getCustomer().getEmail();

        log.info("📡 处理 subscription.update: subscriptionId={} payload={}",
                subscriptionId, JSON.toJSONString(subscription));

        try {
            // 1. 查询用户
            User user = userMapper.findByEmail(email);
            if (user == null) {
                log.warn("⚠️ 未找到订阅更新对应的用户 email={} subscriptionId={}", email, subscriptionId);
                return;
            }

            // 2. 调用更新会员逻辑
            updateUserMembership(user.getId(), subscription, subscriptionId);

            log.info("✅ subscription.update 处理完成 subscriptionId={}", subscriptionId);

        } catch (Exception e) {
            log.error("❌ 处理 subscription.update 失败: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private void handleCheckoutCompleted(CreemWebhookEvent event) {
        CheckoutObject checkout = event.getObject().getCheckout();
        String checkoutId = event.getObject().getId();
        log.info("📡 处理 checkout.completed: checkoutId={}", checkoutId);

        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq("checkout_id", checkoutId));
        if (order == null) {
            log.warn("⚠️ 收到 Creem webhook 但本地查不到订单 checkoutId={}，需要进行补录", checkoutId);
            updateAndSaveOrder(new Order(), checkout, checkoutId);
        } else {
            if (OrderStatus.COMPLETED.equals(order.getStatus())) {
                log.info("订单 {} 已处理过 checkout.completed，跳过", checkoutId);
                return;
            }
            updateAndSaveOrder(order, checkout, checkoutId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleSubscriptionCanceledAndExpired(CreemWebhookEvent event) {

        SubscriptionObject subscription = event.getObject().getSubscription();
        String subscriptionId = event.getObject().getId();
        String email = subscription.getCustomer().getEmail();

        CreemEventType eventType = event.getCreemEventType();
        log.info("📡 处理 subscription.{}: subscriptionId={}, status={}",
                eventType, subscriptionId, subscription.getStatus());

        try {
            // 1. 查用户
            User user = userMapper.findByEmail(email);
            if (user == null) {
                log.warn("⚠️ 未找到 email={} 对应的用户，无法处理 subscriptionId={}", email, subscriptionId);
                return;
            }

            // 2. 查 membership
            UserMembership existing = userMembershipMapper.findBySubscriptionId(subscriptionId);

            if (existing == null) {
                log.error("⚠️ 未找到订阅 {} 对应的会员记录，无法更新状态为 {}", subscriptionId, eventType);
                return;
            }

            // 3. 更新状态
            existing.setStatus(UserMembership.Status.fromValue(subscription.getStatus()));
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setEndTime(subscription.getCurrentPeriodEndDate());

            userMembershipMapper.insertOrUpdate(existing);

            log.info("✅ 订阅 {} 标记为 {}（有效期至 {}）",
                    subscriptionId, existing.getStatus(), existing.getEndTime());

        } catch (Exception e) {
            log.error("❌ 处理 subscription.{} 失败: {}", eventType, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleSubscriptionActiveAndTrialing(CreemWebhookEvent event) {

        SubscriptionObject subscription = event.getObject().getSubscription();
        CreemEventType eventType = event.getCreemEventType();

        log.info("📡 处理 subscription.{}: {}", eventType, JSON.toJSONString(subscription));

        String subscriptionId = event.getObject().getId();
        String email = subscription.getCustomer().getEmail();
        String status = subscription.getStatus();

        try {
            // 1. 查询用户
            User user = userMapper.findByEmail(email);
            if (user == null) {
                user = userService.register(email, "");
            }

            // 2. 查找已有的会员记录（subscriptionId）
            UserMembership existing = userMembershipMapper.findBySubscriptionId(subscriptionId);

            if (existing != null) {
                log.info("🔁 订阅 {} 已存在，跳过重复 active 检查", subscriptionId);

                String currentStatus = existing.getStatus().name().toLowerCase();

                if (currentStatus.equals(status)) {
                    log.info("订阅 {} 状态未变更，跳过", subscriptionId);
                    return;
                }

                // 状态变化 → 更新
                existing.setStatus(UserMembership.Status.fromValue(status));
                existing.setEndTime(subscription.getCurrentPeriodEndDate());
                existing.setUpdatedAt(LocalDateTime.now());
                userMembershipMapper.insertOrUpdate(existing);

                return;
            }

            // 3. 不存在 → 创建新的会员记录
            log.info("🆕 创建新会员记录（subscription.{}），subscriptionId={}", eventType, subscriptionId);

            updateUserMembership(user.getId(), subscription, subscriptionId);

        } catch (Exception e) {
            log.error("❌ 处理 subscription.{} 失败: {}", eventType, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }


    /**
     * 更新订单
     */
    private Order updateAndSaveOrder(Order order, CheckoutObject checkout, String checkoutId) {
        Long id = order.getId();
        order.setRequestId(checkout.getRequestId());
        order.setCheckoutId(checkoutId);
        order.setUnits(checkout.getUnits());
        order.setOrderId(checkout.getOrder().getId());
        order.setCustomerId(checkout.getCustomer().getId());
        order.setStatus(checkout.getStatus());
        order.setUpdatedAt(LocalDateTime.now());
        //没有用户id就查找或者注册用户
        if (order.getUserId() == null) {
            String email = checkout.getCustomer().getEmail();
            User user = userMapper.findByEmail(email);
            if (user == null) {
                user = userService.register(email, "");
            }
            order.setUserId(user.getId());
        }
        //有订单则更新，否则新增订单
        if (id != null) {
            orderMapper.updateById(order);
        } else {
            String productId = checkout.getProduct().getId();
            Product p = productMapper.findByProductId(productId);
            if (p != null) {
                order.setProductId(p.getId());
            }
            order.setCreatedAt(LocalDateTime.now());
            orderMapper.insert(order);
        }
        return order;
    }

    /**
     * 更新或新增用户会员
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserMembership(Long userId, SubscriptionObject subscription, String subscriptionId) {
        String productId = subscription.getProduct().getId();

        try {
            // 1. 查询产品配置 (MyBatis-Plus)
            Product product = productMapper.findByProductId(productId);

            if (product == null) {
                // 对应 switchIfEmpty logic
                log.warn("未找到产品ID为 {} 的商品配置，无法为用户 {} 更新会员信息", productId, userId);
                return;
            }

            // 2. 查询用户现有的会员信息
            UserMembership existing = userMembershipMapper.findByUserId(userId);

            // 3. 分支逻辑：更新 或 新建
            if (existing != null) {

                handleMembershipUpdate(existing, subscription, product, subscriptionId);
            } else {

                createNewMembership(userId, subscription, product, subscriptionId);
            }


            invalidateUserTokens(userId);

        } catch (Exception e) {
            log.error("更新用户 {} 会员信息失败: {}", userId, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /**
     * 会员升级/续费/降级逻辑
     */
    private UserMembership handleMembershipUpdate(UserMembership existing, SubscriptionObject subscription, Product product, String subscriptionId) {
        LocalDateTime now = LocalDateTime.now();
        existing.setUpdatedAt(now);
        existing.setSubscriptionId(subscriptionId);
        existing.setLastTransactionId(subscription.getLastTransactionId());
        Membership oldMembership = membershipMapper.selectById(existing.getMembershipId());
        if (oldMembership == null) {
            // 这种情况通常不应发生，但为了逻辑完整性需处理
            log.error("未找到 ID 为 {} 的旧会员配置", existing.getMembershipId());
            return null;
        }

        Membership newMembership = membershipMapper.findByProductId(product.getId());
        if (newMembership == null) {
            log.warn("未找到产品ID为 {} 的会员配置，无法为用户 {} 更新会员信息", product.getId(), existing.getUserId());
            return null;
        }
        LocalDateTime oldEndTime = existing.getEndTime();

        //判断当前会员是否还有剩余时间
        boolean hasRemainingTime = oldEndTime != null && oldEndTime.isAfter(now);


        if (Objects.equals(oldMembership.getId(), newMembership.getId())) {
            if (hasRemainingTime) {
                existing.setEndTime(subscription.getCurrentPeriodEndDate());
            } else {
                existing.setStartTime(subscription.getCurrentPeriodStartDate());
                existing.setEndTime(subscription.getCurrentPeriodEndDate());
            }
        } else {
            existing.setMembershipId(newMembership.getId());
            existing.setStartTime(now);
            existing.setEndTime(subscription.getCurrentPeriodEndDate());
        }
        existing.setStatus(UserMembership.Status.fromValue(subscription.getStatus()));
        userMembershipMapper.insertOrUpdate(existing);
        return userMembershipMapper.selectById(existing.getId());
    }

    private UserMembership createNewMembership(Long userId, SubscriptionObject subscription, Product product, String subscriptionId) {
        Membership membership = membershipMapper.findByProductId(product.getId());
        LocalDateTime now = LocalDateTime.now();
        UserMembership userMembership = new UserMembership();
        userMembership.setUserId(userId);
        userMembership.setMembershipId(membership.getId());
        userMembership.setStartTime(subscription.getCurrentPeriodStartDate());
        userMembership.setEndTime(subscription.getCurrentPeriodEndDate());
        userMembership.setStatus(UserMembership.Status.fromValue(subscription.getStatus()));
        userMembership.setCreatedAt(now);
        userMembership.setUpdatedAt(now);
        userMembership.setSubscriptionId(subscriptionId);
        userMembership.setLastTransactionId(subscription.getLastTransactionId());
        userMembershipMapper.insert(userMembership);
        return userMembership;
    }

    private void invalidateUserTokens(Long userId) {
        String reason = "Membership updated via checkout";
        boolean errorOccurred = false;

        try {
            tokenService.revokeAllUserTokens(userId, reason);
        } catch (Exception e) {
            log.error("普通令牌撤销失败: {}", e.getMessage(), e);
            errorOccurred = true;
        }

        try {
            oauth2TokenService.revokeAllUserTokens(userId, reason);
        } catch (Exception e) {
            log.error("OAuth2令牌撤销失败: {}", e.getMessage(), e);
            errorOccurred = true;
        }

        if (!errorOccurred) {
            log.info("已使用户 {} 的历史令牌失效", userId);
        } else {
            log.error("用户 {} 令牌失效处理部分或全部失败", userId);
        }
    }

    @Transactional
    public void handleSubscriptionPaid(CreemWebhookEvent event) {
        SubscriptionObject subscription = event.getObject().getSubscription();
        log.info("💰 处理 subscription.paid: {}", JSON.toJSONString(subscription));

        String email = subscription.getCustomer().getEmail();
        String subscriptionId = event.getObject().getId();
        String lastTxn = subscription.getLastTransactionId();
        if (lastTxn == null) {
            log.warn("⚠️ paid webhook 缺少 last_transaction_id，跳过 subscriptionId={}", subscriptionId);
            return;
        }

        User user = userMapper.findByEmail(email);
        if (user == null) {
            user = userService.register(email, "");
        }
        UserMembership existing = userMembershipMapper.findBySubscriptionId(subscriptionId);

        if (existing != null) {
            // === 场景 A: 找到了现有订阅 ===

            // 3.1 幂等性检查：防止重复处理同一个 transaction
            if (Objects.equals(existing.getLastTransactionId(), lastTxn)) {
                log.info("🔁 已处理过 transactionId={}，跳过重复 paid webhook", lastTxn);
                return;
            }

            // 3.2 首次绑定支付信息 vs 后续续费更新
            if (StringUtils.isEmpty(existing.getLastTransactionId())) {
                // 这是一个刚创建但还没关联 Transaction 的记录
                existing.setStatus(UserMembership.Status.valueOf(subscription.getStatus()));
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setLastTransactionId(lastTxn);

                userMembershipMapper.updateById(existing);
                log.info("✅ 已同步订阅 {} 状态为 {}", subscriptionId, existing.getStatus());
            } else {
                // 这是一个续费或状态变更，调用更新逻辑
                updateUserMembership(user.getId(), subscription, subscriptionId);
            }

        } else {
            // === 场景 B: 本地未找到订阅 (对应 switchIfEmpty) ===
            log.info("⚠️ 本地未找到订阅 {}，执行补录创建", subscriptionId);
            updateUserMembership(user.getId(), subscription, subscriptionId);
        }
    }

}
