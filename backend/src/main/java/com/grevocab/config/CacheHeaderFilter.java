package com.grevocab.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 静态资源缓存策略：
 * - index.html / SPA 路由（无扩展名的文档请求）：no-cache —— 每次都向服务器校验，
 *   确保发新版后浏览器立即拿到新入口（手机浏览器对 index.html 的启发式缓存曾导致
 *   用户长期停留在旧版本，表现为改名/新功能全部"没生效"）。
 * - /assets/**（Vite 产物，文件名带内容 hash）：强缓存一年 immutable，文件一变 hash 即变。
 * - /api/** 与其它资源（如 piper 模型）不做干预。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CacheHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest request && res instanceof HttpServletResponse response) {
            String uri = request.getRequestURI();
            String ctx = request.getContextPath();
            String path = (ctx != null && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : uri;

            if (!path.startsWith("/api/")) {
                if (path.startsWith("/assets/")) {
                    response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
                } else if (path.equals("/") || path.equals("/index.html") || !path.contains(".")) {
                    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                    response.setHeader("Pragma", "no-cache");
                    response.setDateHeader("Expires", 0);
                }
            }
        }
        chain.doFilter(req, res);
    }
}
