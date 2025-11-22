package com.logai.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final long CODE_EXPIRATION_MINUTES = 10;
    // 1分钟内只能发送一次
    private static final long CODE_COOLDOWN_SECONDS = 60;
    // 每天最多发送10次
    private static final int MAX_DAILY_CODES = 10;

    public String generateVerificationCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }

    public void saveVerificationCode(String email, String code, String type) {
        String key = buildKey(email, type);
        String dailyCountKey = buildDailyCountKey(email);

        // 设置验证码，10分钟过期
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);

        // 增加每日发送次数
        redisTemplate.opsForValue().increment(dailyCountKey);
        redisTemplate.expire(dailyCountKey, 1, TimeUnit.DAYS);

        log.info("Saved verification code for email: {}, type: {}", email, type);
    }

    public boolean validateVerificationCode(String email, String code, String type) {
        String key = buildKey(email, type);
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode != null && storedCode.equals(code)) {
            // 验证成功后删除验证码
            redisTemplate.delete(key);
            log.info("Verification code validated successfully for email: {}, type: {}", email, type);
            return true;
        }

        log.warn("Invalid verification code for email: {}, type: {}", email, type);
        return false;
    }

    public boolean canSendCode(String email) {
        String cooldownKey = buildCooldownKey(email);
        String dailyCountKey = buildDailyCountKey(email);

        // 检查冷却时间
        if (redisTemplate.hasKey(cooldownKey)) {
            log.warn("邮箱：{}的验证码还在冷却期，无法发送新验证码 ", email);
            return false;
        }

        // 检查每日发送次数
        String dailyCountStr = redisTemplate.opsForValue().get(dailyCountKey);
        if (dailyCountStr != null) {
            int dailyCount = Integer.parseInt(dailyCountStr);
            if (dailyCount >= MAX_DAILY_CODES) {
                log.warn("邮箱：{}的验证码已发送超过{}次，无法发送新验证码 ", email, MAX_DAILY_CODES);
                return false;
            }
        }

        return true;
    }

    public void setCooldown(String email) {
        String cooldownKey = buildCooldownKey(email);
        redisTemplate.opsForValue().set(cooldownKey, "1", CODE_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private String buildKey(String email, String type) {
        return "verification_code:" + type + ":" + email;
    }

    private String buildCooldownKey(String email) {
        return "verification_cooldown:" + email;
    }

    private String buildDailyCountKey(String email) {
        return "verification_daily_count:" + email;
    }
}