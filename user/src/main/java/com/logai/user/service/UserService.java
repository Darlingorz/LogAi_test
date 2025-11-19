package com.logai.user.service;

import com.logai.user.dto.UserInfoResponse;
import com.logai.user.entity.User;

public interface UserService {
    /**
     * 用户登录（邮箱验证码）
     *
     * @param email            邮箱
     * @param verificationCode 验证码
     * @param deviceId         设备ID
     * @param ipAddress        IP地址
     * @param userAgent        用户代理
     * @return 用户信息（包含Access Token和Refresh Token）
     */
    User loginWithEmailVerification(String email, String verificationCode, String deviceId, String ipAddress, String userAgent, String timeZone);

    UserInfoResponse getUserInfo(User user);

    /**
     * 开始试用
     *
     * @param user 用户
     * @return 是否成功
     */
    void startTrial(User user);
}
