package com.chaitin.niuniuwiki.common;

import org.springframework.http.HttpStatus;

/**
 * 提供 NiuniuWiki 后端的公共基础基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-07-11
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    public ApiException(String message) {
        this(HttpStatus.OK, message, 0);
    }

    public ApiException(HttpStatus status, String message) {
        this(status, message, 0);
    }

    public ApiException(HttpStatus status, String message, int code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public int code() {
        return code;
    }
}
