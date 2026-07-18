package com.chaitin.niuniuwiki.common;

import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 提供 NiuniuWiki 后端的公共基础基础能力。
 *
 * @author 程序员牛肉
 * @since 2026-06-01
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.getMessage(), exception.code()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleInvalidRequest(Exception exception) {
        return ApiResponse.error("invalid request: " + rootMessage(exception));
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ApiResponse<Void> handleNotFound(EmptyResultDataAccessException exception) {
        return ApiResponse.error("Not Found", 40004);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled API error, trace_id={}", traceId, exception);
        return ApiResponse.error("Internal Error [trace_id: " + traceId + "]", 50001);
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? exception.getClass().getSimpleName() : current.getMessage();
    }
}
