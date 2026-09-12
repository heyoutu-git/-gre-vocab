package com.grevocab.auth.dto;

import lombok.Data;

@Data
public class CaptchaResponse {
    private String token;       // 验证码令牌，登录时回传用于服务端校验
    private String imageBase64; // PNG 图片的 base64（不含 data: 前缀，前端拼 data:image/png;base64,）
}
