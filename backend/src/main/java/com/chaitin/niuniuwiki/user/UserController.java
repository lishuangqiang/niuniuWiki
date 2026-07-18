package com.chaitin.niuniuwiki.user;

import com.chaitin.niuniuwiki.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理用户管理相关的 HTTP 请求。
 *
 * @author 程序员牛肉
 * @since 2026-05-20
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    public UserController(UserService userService, LoginAttemptService loginAttemptService) {
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(
            @Valid @RequestBody UserDtos.LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        loginAttemptService.ensureNotLocked(ip);
        try {
            String token = userService.login(request.account(), request.password());
            loginAttemptService.succeeded(ip);
            return ApiResponse.ok(Map.of("token", token));
        } catch (RuntimeException exception) {
            loginAttemptService.failed(ip);
            throw exception;
        }
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> currentUser() {
        return ApiResponse.ok(userService.currentUser());
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list() {
        return ApiResponse.ok(Map.of("users", userService.list()));
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, String>> create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return ApiResponse.ok(Map.of("id", userService.create(request)));
    }

    @PutMapping("/reset_password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody UserDtos.ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam("user_id") String userId) {
        userService.delete(userId);
        return ApiResponse.ok(null);
    }
}
