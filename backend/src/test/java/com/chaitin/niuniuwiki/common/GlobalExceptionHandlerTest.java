package com.chaitin.niuniuwiki.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/**
 * 固化公共错误协议的 HTTP 语义，防止业务异常再次被伪装成成功响应。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsBadRequestForDefaultDomainFailure() {
        var response = handler.handleApiException(new ApiException("参数不合法"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("参数不合法");
    }

    @Test
    void hidesUnexpectedExceptionAndReturnsTraceId() {
        MDC.put("trace_id", "trace-test");
        try {
            var response = handler.handleUnexpected(new IllegalStateException("数据库口令不应返回"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo("Internal Error [trace_id: trace-test]");
            assertThat(response.getBody().message()).doesNotContain("数据库口令");
        } finally {
            MDC.clear();
        }
    }
}
