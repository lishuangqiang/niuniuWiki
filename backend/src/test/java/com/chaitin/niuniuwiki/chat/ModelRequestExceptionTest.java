package com.chaitin.niuniuwiki.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 验证上游模型错误的分类语义。
 *
 * @author 程序员牛肉
 * @since 2026-07-18
 */
class ModelRequestExceptionTest {

    @Test
    void distinguishesInvalidParametersFromContextOverflow() {
        ModelRequestException invalidParameter = new ModelRequestException(400, "Upstream request failed");
        ModelRequestException contextOverflow = new ModelRequestException(
                400, "maximum context length exceeded");

        assertThat(invalidParameter.failureKind())
                .isEqualTo(ModelRequestException.FailureKind.INVALID_REQUEST);
        assertThat(invalidParameter.canRetryWithoutTokenLimit()).isTrue();
        assertThat(contextOverflow.failureKind())
                .isEqualTo(ModelRequestException.FailureKind.CONTEXT_LIMIT);
        assertThat(contextOverflow.isContextLimit()).isTrue();
        assertThat(contextOverflow.canRetryWithoutTokenLimit()).isFalse();
    }
}
