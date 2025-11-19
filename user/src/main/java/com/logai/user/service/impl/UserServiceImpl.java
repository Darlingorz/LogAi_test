package com.logai.user.service.impl;

import com.logai.common.service.VerificationCodeService;
import com.logai.creem.entity.Membership;
import com.logai.creem.entity.UserMembership;
import com.logai.creem.mapper.MembershipMapper;
import com.logai.creem.mapper.UserMembershipMapper;
import com.logai.security.dto.TokenResponse;
import com.logai.security.service.TokenService;
import com.logai.user.dto.UserInfoResponse;
import com.logai.user.entity.User;
import com.logai.user.mapper.UserMapper;
import com.logai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final TransactionalOperator transactionalOperator;
    private final VerificationCodeService verificationCodeService;
    private final MembershipMapper membershipMapper;
    private final UserMembershipMapper userMembershipMapper;
    private final TokenService tokenService;

    @Value("${featurebase.secret-key}")
    private String featurebaseSecretKey;

    public User register(User user, String deviceId, String ipAddress, String userAgent) {
        user.setUuid(UUID.randomUUID().toString());
        userMapper.insertOrUpdate(user);
        TokenResponse tokenResponse = tokenService.generateTokens(user, deviceId, ipAddress, userAgent);
        user.setAccessToken(tokenResponse.getAccessToken());
        user.setRefreshToken(tokenResponse.getRefreshToken());
        startTrial(user);
        return user;
    }

    @Override
    @Transactional
    public User loginWithEmailVerification(String email, String verificationCode, String deviceId, String ipAddress, String userAgent, String timeZone) {
        // 验证验证码
        if (!verificationCodeService.validateVerificationCode(email, verificationCode, "login")) {
            log.warn("电子邮件的验证码无效：{}", email);
            return null;
        }
        User user = userMapper.findByEmail(email);
        if (user == null) {
            log.warn("找不到电子邮件{}的用户", email);
            log.info("找不到电子邮件{}的用户，正在自动注册...", email);
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setTimeZone(timeZone);
            newUser.setUsername(email.split("@")[0]);
            newUser.setPassword("");
            newUser.setStatus("1");
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());

            user = register(newUser, deviceId, ipAddress, userAgent);
        }

        TokenResponse tokenResponse = tokenService.generateTokens(user, deviceId, ipAddress, userAgent);
        user.setAccessToken(tokenResponse.getAccessToken());
        user.setRefreshToken(tokenResponse.getRefreshToken());

        return user;
    }

    @Override
    public UserInfoResponse getUserInfo(User user) {
        UserInfoResponse userInfo = userMapper.findUserInfo(user.getId());
        UserMembership userMembership = userMembershipMapper.findByUserId(user.getId());
        userInfo.setNotDoneFreeTrail(!userMembership.getStatus().equals(UserMembership.Status.ACTIVE));
        String featurebaseUserHash = userInfo.getFeaturebaseUserHash();
        if (StringUtils.isEmpty(featurebaseUserHash)) {
            try {
                String newHash = calculateHmacSha256(userInfo.getEmail(), featurebaseSecretKey);
                user.setFeaturebaseUserHash(newHash);
                userMapper.insertOrUpdate(user);
                userInfo.setFeaturebaseUserHash(newHash);
                return userInfo;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate featurebase user hash", e);
            }
        }
        return userInfo;
    }

    @Override
    public void startTrial(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("User or User ID cannot be null");
        }
        UserMembership existingMembership = userMembershipMapper.findByUserId(user.getId());
        if (existingMembership != null) {
            throw new IllegalStateException("User has already used trial");
        } else {
            createTrialMembershipForUser(user);
        }
    }

    /**
     * 为用户创建试用会员资格
     *
     * @param user 用户对象
     * @return Mono<Void>
     */
    private void createTrialMembershipForUser(User user) {
        Membership trialMembership = membershipMapper.findByName("trial");
        if (trialMembership == null) {
            throw new IllegalStateException("Trial membership plan not found");
        }
        final LocalDateTime now = LocalDateTime.now();
        UserMembership newUserMembership = new UserMembership();
        newUserMembership.setUserId(user.getId());
        newUserMembership.setMembershipId(trialMembership.getId());
        newUserMembership.setStatus(UserMembership.Status.TRIALING);
        newUserMembership.setStartTime(now);
        newUserMembership.setEndTime(now.plusDays(7));
        newUserMembership.setCreatedAt(now);
        newUserMembership.setUpdatedAt(now);
        userMembershipMapper.insert(newUserMembership);
    }

    private String calculateHmacSha256(String data, String secretKey) throws Exception {
        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256HMAC.init(secretKeySpec);
        byte[] hash = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
