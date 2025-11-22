package com.logai.oauth2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private String sub;           // 用户ID
    private String name;          // 姓名
    private String email;         // 邮箱
    private String picture;       // 头像
    private boolean emailVerified; // 邮箱是否验证
    private String error;
    private String errorDescription;

    public UserInfoResponse(String error, String errorDescription) {
        this.error = error;
        this.errorDescription = errorDescription;
    }
}