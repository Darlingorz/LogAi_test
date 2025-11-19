package com.logai.user.dto;

import lombok.Data;

@Data
public class GoogleUserInfo {
    private String sub;           // Google user ID
    private String name;          // Full name
    private String given_name;    // First name
    private String family_name;   // Last name
    private String picture;       // Profile picture URL
    private String email;         // Email address
    private Boolean email_verified;
    private String locale;        // Language preference
    private String aud;           // Audience (should match your client ID)
}