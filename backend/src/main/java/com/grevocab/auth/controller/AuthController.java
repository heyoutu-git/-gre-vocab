package com.grevocab.auth.controller;

import com.grevocab.auth.dto.AuthResponse;
import com.grevocab.auth.dto.CaptchaResponse;
import com.grevocab.auth.dto.LoginRequest;
import com.grevocab.auth.dto.RegisterRequest;
import com.grevocab.auth.dto.UserInfo;
import com.grevocab.auth.service.AuthService;
import com.grevocab.auth.service.CaptchaService;
import com.grevocab.common.JwtUtil;
import com.grevocab.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, JwtUtil jwtUtil, CaptchaService captchaService) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest req) {
        authService.register(req);
        return Result.success("注册成功，等待管理员审核通过后即可登录");
    }

    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() {
        return Result.success(captchaService.generate());
    }

    @GetMapping("/me")
    public Result<UserInfo> me(HttpServletRequest request) {
        Long uid = (Long) request.getAttribute("userId");
        return Result.success(authService.me(uid));
    }
}
