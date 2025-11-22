package com.logai.security.controller;

import com.logai.common.model.Result;
import com.logai.common.utils.HttpRequestUtil;
import com.logai.security.dto.RefreshTokenRequest;
import com.logai.security.dto.TokenInfo;
import com.logai.security.dto.TokenResponse;
import com.logai.security.entity.RefreshToken;
import com.logai.security.service.TokenService;
import com.logai.security.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;

    /**
     * 刷新Access Token
     * 支持多种请求格式：
     * 1. JSON请求体（标准方式）
     */
    @PostMapping("/refresh")
    public Result refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        RefreshTokenRequest actualRequest = request;
        if (actualRequest == null) {
            log.debug("请求体为空，创建新的 RefreshTokenRequest");
            actualRequest = new RefreshTokenRequest();
        } else {
            log.debug("请求体中包含 refreshToken，长度为: {}",
                    actualRequest.getRefreshToken() != null ? actualRequest.getRefreshToken().length() : 0);
        }

        // 验证必需的参数
        if (actualRequest.getRefreshToken() == null || actualRequest.getRefreshToken().isEmpty()) {
            log.warn("令牌刷新请求缺少refreshToken - body: {}",
                    request != null ? "present" : "null");
            return Result.failure(400, "refreshToken不能为空");
        }

        // 获取客户端IP地址
        String clientIp = HttpRequestUtil.getClientIp(httpRequest);
        actualRequest.setIpAddress(clientIp);

        // 获取User-Agent
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent != null) {
            actualRequest.setUserAgent(userAgent);
        }

        String deviceId = actualRequest.getDeviceId() != null ? actualRequest.getDeviceId() : "unknown";
        log.debug("令牌刷新请求来自IP: {}, 设备: {}", clientIp, deviceId);
        try {
            TokenResponse tokenResponse = tokenService.refreshAccessToken(actualRequest);
            log.debug("令牌刷新成功，设备: {}", deviceId);

            String jwt = tokenResponse.getAccessToken();

            // 创建 Cookie
            Cookie cookie = new Cookie("session", jwt);
            cookie.setHttpOnly(true); // JS不可访问
            cookie.setSecure(true);   // 仅HTTPS
            cookie.setPath("/");      // 全站有效
            cookie.setMaxAge(8 * 60 * 60); // 8 小时
            cookie.setAttribute("SameSite", "None");

            httpResponse.addCookie(cookie);

            return Result.success(tokenResponse);
        } catch (Exception e) {
            log.error("令牌刷新失败: {}", e.getMessage());
            if (e.getMessage().contains("Invalid or expired refresh token")) {
                return Result.failure(401, "刷新令牌无效或已过期");
            }
            return Result.failure(500, "令牌刷新失败: " + e.getMessage());
        }


    }

    /**
     * 废除Refresh Token（登出）
     */
    @PostMapping("/logout")
    public Result logout(HttpServletResponse response,
                         @RequestBody(required = false) RefreshTokenRequest request) {

        String refreshToken = null;

        // 从请求体获取refresh token
        if (request != null && request.getRefreshToken() != null) {
            refreshToken = request.getRefreshToken();
        }

        if (refreshToken == null || refreshToken.isEmpty()) {
            log.warn("没有刷新令牌的注销请求");
            return Result.failure(400, "Refresh token不能为空");
        }

        log.info("注销请求包含刷新令牌");
        try {
            tokenService.revokeRefreshToken(refreshToken, "User logout");
            Cookie deleteCookie = new Cookie("session", "");
            deleteCookie.setHttpOnly(true); // JS不可访问
            deleteCookie.setSecure(true);   // 仅HTTPS
            deleteCookie.setPath("/");      // 全站有效
            deleteCookie.setMaxAge(0); // 8 小时
            deleteCookie.setAttribute("SameSite", "None");

            response.addCookie(deleteCookie);
            return Result.success("登出成功");
        } catch (Exception e) {
            log.error("注销失败: {}", e.getMessage());
            return Result.failure(500, "注销失败: " + e.getMessage());
        }
    }

    /**
     * 废除指定用户的所有Token（管理员功能）
     */
    @PostMapping("/revoke-all/{userId}")
    public Result revokeAllUserTokens(@PathVariable Long userId,
                                      @RequestParam(required = false) String reason) {
        try {
            String revokeReason = reason != null ? reason : "Admin revocation";
            log.info("正在吊销用户{}的所有令牌，原因：{}", userId, revokeReason);
            tokenService.revokeAllUserTokens(userId, revokeReason);
            log.info("已吊销用户的所有令牌: {}", userId);
            return Result.success("用户所有Token已废除");
        } catch (Exception e) {
            log.error("吊销用户所有令牌失败: {}", userId, e);
            return Result.failure(500, "废除用户Token失败: " + e.getMessage());
        }


    }

    /**
     * 获取用户活跃Token列表（需要认证）
     */
    @GetMapping("/tokens/active")
    public Result getActiveTokens(@RequestHeader("Authorization") String authorization) {
        // 从Authorization头提取用户ID
        try {
            Long userId = extractUserIdFromToken(authorization);
            log.info("获取用户活跃Token列表 - 用户: {}", userId);
            List<RefreshToken> userActiveTokens = tokenService.getUserActiveTokens(userId);
            log.info("获取用户活跃Token列表 - 找到 {} 个活跃Token - 用户: {}", userActiveTokens.size(), userId);
            return Result.success(userActiveTokens);
        } catch (Exception e) {
            return Result.failure(500, "获取用户活跃Token列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取Token缓存统计信息（管理功能）
     */
    @GetMapping("/stats")
    public Mono<Result> getTokenStats() {
        return Mono.fromCallable(() -> {
            var stats = tokenService.getTokenCacheStats();
            log.info("Token cache stats: {}", stats);
            return Result.success(stats);
        });
    }

    /**
     * 清理过期Token（定时任务调用）
     */
    @PostMapping("/cleanup")
    public Result cleanupExpiredTokens() {
        log.info("Starting token cleanup task");
        try {
            Integer count = tokenService.cleanupExpiredTokens();
            return Result.success("清理了 " + count + " 个过期Token");
        } catch (Exception e) {
            return Result.failure(500, "清理过期Token失败: " + e.getMessage());

        }
    }

    /**
     * 检查Token有效性（验证接口）
     */
    @GetMapping("/validate")
    public Result validateToken(@RequestHeader("Authorization") String authorization) {
        String token = extractTokenFromHeader(authorization);

        if (token == null) {
            return Result.failure(400, "Token不能为空");
        }
        TokenInfo tokenInfo = tokenService.validateAccessToken(token);
        if (tokenInfo == null) {
            return Result.failure(401, "Token无效或已过期");
        }
        log.debug("用户的令牌验证成功：{}", tokenInfo.getUserUuid());
        return Result.success("Token有效");
    }

    // === 辅助方法 ===

    /**
     * 从Authorization头中提取Token
     */
    private String extractTokenFromHeader(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    /**
     * 从Token中提取用户ID（简化实现）
     */
    private Long extractUserIdFromToken(String authorization) {
        String token = extractTokenFromHeader(authorization);
        if (token == null) {
            // 无效的Authorization头
            throw new RuntimeException("Invalid Authorization header");
        }
        return Long.parseLong(jwtUtils.getUserIdFromToken(token));
    }
}
