package com.chaitin.niuniuwiki.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 定义用户管理模块的请求与响应数据结构。
 *
 * @author 程序员牛肉
 * @since 2026-06-29
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record LoginRequest(
            @NotBlank String account,
            @NotBlank String password
    ) {
    }

    public record CreateUserRequest(
            @NotBlank String account,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Pattern(regexp = "admin|user") String role
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String id,
            @NotBlank @Size(min = 8) String newPassword
    ) {
    }
}
