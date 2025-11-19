package com.logai.security.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token加密服务
 * 使用AES-256-GCM加密算法确保Token安全存储
 */
@Slf4j
@Component
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public TokenEncryptionService(@Value("${token.encryption.key:}") String encryptionKey) {
        this.secureRandom = new SecureRandom();
        this.secretKey = initializeKey(encryptionKey);
    }

    /**
     * 初始化加密密钥
     */
    private SecretKey initializeKey(String providedKey) {
        try {
            if (providedKey != null && !providedKey.isEmpty()) {
                // 使用配置的密钥
                byte[] decodedKey = Base64.getDecoder().decode(providedKey);
                return new SecretKeySpec(decodedKey, "AES");
            } else {
                // 生成新的密钥（仅用于测试，生产环境必须配置密钥）
                log.warn("JWT 加密服务未配置加密密钥, 生成随机密钥. 这仅用于测试, 生产环境必须配置密钥!");
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(KEY_LENGTH);
                return keyGenerator.generateKey();
            }
        } catch (Exception e) {
            log.error("JWT 加密服务初始化密钥失败", e);
            throw new RuntimeException("Failed to initialize JWT encryption service key", e); // JWT 加密服务初始化密钥失败
        }
    }

    /**
     * 加密Token
     */
    public String encryptToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);

            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(token.getBytes("UTF-8"));

            // 组合IV和密文
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("JWT 令牌加密失败", e);
            throw new RuntimeException("Failed to encrypt JWT token", e); // JWT 令牌加密失败
        }
    }

    /**
     * 解密Token
     */
    public String decryptToken(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isEmpty()) {
            return null;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedToken);

            // 分离IV和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            log.error("JWT 令牌解密失败", e);
            throw new RuntimeException("Failed to decrypt JWT token", e); // JWT 令牌解密失败
        }
    }

    /**
     * 生成安全的随机Token（用于Refresh Token）
     */
    public String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 生成Token哈希（用于数据库索引）
     */
    public String generateTokenHash(String token) {
        try {
            // 使用加密密钥对Token进行HMAC，生成固定长度的哈希
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] hashBytes = mac.doFinal(token.getBytes("UTF-8"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (Exception e) {
            log.error("JWT 令牌生成哈希失败", e);
            throw new RuntimeException("Failed to generate JWT token hash", e); // JWT 令牌生成哈希失败
        }
    }
}