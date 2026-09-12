package com.grevocab.auth.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
}
