package com.chaitin.niuniuwiki.security;

import com.chaitin.niuniuwiki.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 提供 NiuniuWiki 后端的安全认证基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-06-12
 */
@Component
public class AuthInterceptor implements AsyncHandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        AuthContext.set(authService.authenticate(header.substring(7)));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        AuthContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        AuthContext.clear();
    }
}
