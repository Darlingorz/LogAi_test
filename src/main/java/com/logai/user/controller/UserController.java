package com.logai.user.controller;

import com.logai.common.model.Result;
import com.logai.common.service.AssessmentService;
import com.logai.common.service.EmailService;
import com.logai.common.service.VerificationCodeService;
import com.logai.common.utils.HttpRequestUtil;
import com.logai.user.dto.EmailVerificationRequest;
import com.logai.user.entity.User;
import com.logai.user.service.SocialLoginService;
import com.logai.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final AssessmentService assessmentService;
    private final SocialLoginService socialLoginService;


    /**
     * 用户登录接口（邮箱验证码）
     */
    @PostMapping("/login")
    public Result login(@RequestBody EmailVerificationRequest request,
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

        String email = request.getEmail();
        String code = request.getCode();

        // 读取 Header
        String deviceId = httpRequest.getHeader("X-Device-ID");
        String userAgent = httpRequest.getHeader("User-Agent");
        String timeZone = httpRequest.getHeader("X-Time-Zone");

        String clientIp = HttpRequestUtil.getClientIp(httpRequest);

        log.info("尝试登录电子邮件：{}，设备：{}，ip:{}, 时区:{}", email, deviceId, clientIp, timeZone);

        try {
            User user = userService.loginWithEmailVerification(
                    email, code, deviceId, clientIp, userAgent, timeZone
            );

            if (user == null) {
                return Result.failure(401, "邮箱或验证码错误");
            }

            log.info("电子邮件{}登录成功", email);

            // 创建 Cookie
            String jwt = user.getAccessToken();
            Cookie cookie = new Cookie("session", jwt);
            cookie.setHttpOnly(true); // JS不可访问
            cookie.setSecure(true);   // 仅HTTPS
            cookie.setPath("/");      // 全站有效
            cookie.setMaxAge(8 * 60 * 60); // 8 小时
            cookie.setAttribute("SameSite", "None");

            httpResponse.addCookie(cookie);

            return Result.success(user);

        } catch (Exception e) {
            log.error("电子邮件{}登录失败：{}", email, e.getMessage(), e);
            return Result.failure(500, "登录失败: " + e.getMessage());
        }
    }

    /**
     * 发送邮箱验证码接口
     */
    @PostMapping("/getCode")
    public Result getCode(@RequestBody Map<String, String> info
            , HttpServletRequest request) {

        String email = info.get("email");
        String recaptchaToken = info.get("recaptchaToken");
        String type = info.get("type");

        String clientIp = HttpRequestUtil.getClientIp(request);

        if (email == null || email.trim().isEmpty()) {
            return Result.failure("邮箱不能为空");
        }
        try {

//            List<String> assessmentResults = assessmentService.createAssessment(recaptchaToken, "LOGIN", clientIp);
//            if (!assessmentResults.isEmpty()) {
//                return Result.failure("Assessment failed !", assessmentResults);
//            }
            if (!verificationCodeService.canSendCode(email)) {
                throw new RuntimeException("Too many requests. Please try again later."); // 发送过于频繁，请稍后再试
            }
            // 生成验证码
            String code = verificationCodeService.generateVerificationCode();

            // 发送邮件
            emailService.sendVerificationEmail(email, code, type);

            // 保存验证码到Redis
            verificationCodeService.saveVerificationCode(email, code, type);

            // 设置冷却时间
            verificationCodeService.setCooldown(email);

            return Result.success("验证码发送成功");

        } catch (Exception e) {
            log.error("发送验证码失败：{}", e.getMessage(), e);
            return Result.failure(e.getMessage());
        }
    }

    @GetMapping("/getUserInfo")
    public Result getUserInfo(@AuthenticationPrincipal User user) {
        if (user == null) {
            return Result.failure("用户不存在");
        }
        return Result.success(userService.getUserInfo(user));
    }

    /**
     * 前端Code Flow登录（推荐方式）
     */
    @PostMapping("/google/login")
    public Result googleLogin(@RequestBody Map<String, String> request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        String credential = request.get("credential");
        String deviceId = httpRequest.getHeader("X-Device-ID");
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = HttpRequestUtil.getClientIp(httpRequest);
        String timeZone = httpRequest.getHeader("X-Time-Zone");


        if (credential == null || credential.isEmpty()) {
            return Result.failure("缺少授权码");
        }
        try {
            User user = socialLoginService.handleGoogleLogin(credential, deviceId, clientIp, userAgent, timeZone);

            String jwt = user.getAccessToken();
            Cookie cookie = new Cookie("session", jwt);
            cookie.setHttpOnly(true); // JS不可访问
            cookie.setSecure(true);   // 仅HTTPS
            cookie.setPath("/");      // 全站有效
            cookie.setMaxAge(8 * 60 * 60); // 8 小时
            cookie.setAttribute("SameSite", "None");

            httpResponse.addCookie(cookie);
            return Result.success(user);
        } catch (Exception ex) {
            log.error("Google login failed: {}", ex.getMessage());
            return Result.failure("Google登录失败: " + ex.getMessage());
        }


    }

    @GetMapping("/startTrial") // 1. 使用 POST 请求
    public Result startTrial(@AuthenticationPrincipal User user) {
        userService.startTrial(user);
        return Result.success("开启试用成功！");
    }


//    /**
//     * 用户注册接口（邮箱验证码）
//     */
//    @PostMapping("/register")
//    public Mono<Result> register(@RequestBody EmailRegistrationRequest request) {
//        String email = request.getEmail();
//        String code = request.getCode();
//        String username = request.getUsername();
//        String phone = request.getPhone();
//
//        if (email == null || email.trim().isEmpty()) {
//            return Mono.just(Result.error("邮箱不能为空"));
//        }
//        if (code == null || code.trim().isEmpty()) {
//            return Mono.just(Result.error("验证码不能为空"));
//        }
//        if (username == null || username.trim().isEmpty()) {
//            return Mono.just(Result.error("用户名不能为空"));
//        }
//
//        return userService.registerWithEmailVerification(email, code, username, phone)
//                .map(user -> user != null ? Result.success(user) : Result.error("注册失败"))
//                .onErrorReturn(Result.error("注册失败"));
//    }

}
