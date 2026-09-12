package com.grevocab.auth.interceptor;

import com.grevocab.common.BizException;
import com.grevocab.common.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只拦截带 @RequestMapping 的接口方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String uri = request.getRequestURI();
        String method = request.getMethod();
        // 公开端点：认证接口、TTS 合成、预检
        if (uri.contains("/api/auth/")
                || uri.contains("/api/tts/engines")
                || uri.contains("/api/tts/tencent/synthesize")
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        // 书本目录只读（GET）允许匿名浏览：列表 / 详情 / 学习计划看板。
        // 其余书本写操作（导入/改计划/重切/删除）与课时内容仍需登录。
        if ("GET".equalsIgnoreCase(method)
                && (uri.matches(".*/api/books$")
                    || uri.matches(".*/api/books/\\d+$")
                    || uri.matches(".*/api/books/\\d+/plan$"))) {
            return allowAnonymous(request);
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(401, "未登录或令牌缺失");
        }
        String token = header.substring(7);
        try {
            Long uid = jwtUtil.getUserId(token);
            request.setAttribute("userId", uid);
            request.setAttribute("username", jwtUtil.getUsername(token));
            request.setAttribute("role", jwtUtil.getRole(token));
        } catch (Exception e) {
            throw new BizException(401, "令牌无效或已过期");
        }
        return true;
    }

    // 匿名可读：若带有效令牌则解析并注入 userId（用于个性化），否则以匿名身份放行
    private boolean allowAnonymous(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                request.setAttribute("userId", jwtUtil.getUserId(token));
                request.setAttribute("username", jwtUtil.getUsername(token));
                request.setAttribute("role", jwtUtil.getRole(token));
            } catch (Exception ignored) { /* 令牌无效则按匿名处理 */ }
        }
        return true;
    }
}
