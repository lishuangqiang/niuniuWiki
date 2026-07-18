package com.chaitin.niuniuwiki.chat;

import com.chaitin.niuniuwiki.common.ApiException;
import java.util.Locale;

/**
 * 保存上游推理请求的可判定失败类型，供重试与用户提示逻辑做精确决策。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
public final class ModelRequestException extends ApiException {

    private final int upstreamStatus;
    private final FailureKind failureKind;

    public ModelRequestException(int upstreamStatus, String upstreamMessage) {
        super("模型请求失败(" + upstreamStatus + "): " + upstreamMessage);
        this.upstreamStatus = upstreamStatus;
        this.failureKind = classify(upstreamStatus, upstreamMessage);
    }

    public int upstreamStatus() {
        return upstreamStatus;
    }

    public FailureKind failureKind() {
        return failureKind;
    }

    public boolean isContextLimit() {
        return failureKind == FailureKind.CONTEXT_LIMIT;
    }

    public boolean canRetryWithoutTokenLimit() {
        return failureKind == FailureKind.INVALID_REQUEST;
    }

    private static FailureKind classify(int status, String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403) {
            return FailureKind.AUTHENTICATION;
        }
        if (status == 429) {
            return FailureKind.RATE_LIMIT;
        }
        if (status == 413 || containsAny(normalized,
                "context length", "context window", "maximum context", "too many tokens",
                "prompt is too long", "input is too long", "上下文", "超出最大长度")) {
            return FailureKind.CONTEXT_LIMIT;
        }
        if (status == 400 || status == 422) {
            return FailureKind.INVALID_REQUEST;
        }
        if (status >= 500) {
            return FailureKind.UPSTREAM_UNAVAILABLE;
        }
        return FailureKind.UNKNOWN;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public enum FailureKind {
        AUTHENTICATION,
        RATE_LIMIT,
        CONTEXT_LIMIT,
        INVALID_REQUEST,
        UPSTREAM_UNAVAILABLE,
        UNKNOWN
    }
}
