package com.grevocab.auth.service;

import com.grevocab.auth.dto.AuthResponse;
import com.grevocab.auth.dto.LoginRequest;
import com.grevocab.auth.dto.RegisterRequest;
import com.grevocab.auth.dto.UserInfo;

public interface AuthService {

    AuthResponse login(LoginRequest req);

    void register(RegisterRequest req);

    UserInfo me(Long userId);
}
