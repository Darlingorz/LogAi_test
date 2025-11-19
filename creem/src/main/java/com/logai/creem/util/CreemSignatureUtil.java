package com.logai.creem.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.stream.Collectors;

public class CreemSignatureUtil {

    /**
     * 验证 Webhook 签名
     *
     * @param body      原始请求体
     * @param secret    Webhook Secret
     * @param signature 请求头中的 creem-signature
     */
    public static boolean verifyWebhookSignature(String body, String secret, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(digest);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 Return URL 签名
     *
     * @param params    所有查询参数（不包含 signature）
     * @param apiKey    Creem API Key
     * @param signature URL 里的 signature
     */
    public static boolean verifyReturnUrlSignature(Map<String, String> params, String apiKey, String signature) {
        try {
            String data = params.entrySet().stream()
                    .filter(e -> e.getValue() != null && !"signature".equals(e.getKey()))
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("|")) + "|salt=" + apiKey;

            System.out.println("待签名数据: " + data);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);

            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
