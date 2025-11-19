package com.logai.user.service;

import com.logai.user.entity.User;


public interface SocialLoginService {
    User handleGoogleLogin(String credential, String deviceId, String clientIp, String userAgent, String timeZone);
}