package com.chaitin.niuniuwiki.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 提供 NiuniuWiki 后端的公共基础基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-06-09
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String message, boolean success, T data, int code) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("", true, data, 0);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(message, false, null, 0);
    }

    public static ApiResponse<Void> error(String message, int code) {
        return new ApiResponse<>(message, false, null, code);
    }
}
