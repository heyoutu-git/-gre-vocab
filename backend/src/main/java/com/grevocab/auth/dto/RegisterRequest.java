package com.grevocab.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private Boolean agreedTerms;   // 是否同意隐私条款
}
