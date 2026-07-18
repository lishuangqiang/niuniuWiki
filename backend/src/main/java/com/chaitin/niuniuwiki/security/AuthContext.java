package com.chaitin.niuniuwiki.security;

import com.chaitin.niuniuwiki.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 提供 NiuniuWiki 后端的安全认证基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-06-07
 */
public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthPrincipal get() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
