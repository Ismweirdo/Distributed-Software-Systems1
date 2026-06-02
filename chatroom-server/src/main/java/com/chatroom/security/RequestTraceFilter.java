package com.chatroom.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveOrGenerate(request.getHeader(TRACE_ID_HEADER));
        String requestId = resolveOrGenerate(request.getHeader(REQUEST_ID_HEADER));

        request.setAttribute(TRACE_ID_HEADER, traceId);
        request.setAttribute(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            response.setHeader(TRACE_ID_HEADER, traceId);
            response.setHeader(REQUEST_ID_HEADER, requestId);
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(REQUEST_ID_MDC_KEY);
            MDC.remove("userId");
            MDC.remove("username");
        }
    }

    private String resolveOrGenerate(String value) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
