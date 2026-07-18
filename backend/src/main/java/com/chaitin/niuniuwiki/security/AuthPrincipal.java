package com.chaitin.niuniuwiki.security;

/**
 * 提供 NiuniuWiki 后端的安全认证基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-04-24
 */
public record AuthPrincipal(
        String userId,
        boolean apiToken,
        String kbId,
        String permission,
        String role
) {
}
