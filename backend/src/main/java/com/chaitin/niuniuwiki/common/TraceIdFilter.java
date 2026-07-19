package com.chaitin.niuniuwiki.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为 HTTP、日志和上游诊断统一传递请求 Trace ID。
 *
 * @author 程序员牛肉
 * @since 2026-07-19
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = incoming != null && incoming.matches("[A-Za-z0-9._:-]{8,128}")
                ? incoming : UUID.randomUUID().toString();
        MDC.put("trace_id", traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("trace_id");
        }
    }
}
