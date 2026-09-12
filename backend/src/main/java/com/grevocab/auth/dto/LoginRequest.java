package com.grevocab.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    private Boolean agreedTerms;   // 是否同意隐私条款
    private String captcha;        // 图形验证码文本
    private String captchaToken;  // 验证码令牌（与 /api/auth/captcha 返回的 token 对应）
}
