package com.chatroom.security;

import com.chatroom.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String AUTH_STATUS_HEADER = "X-Auth-Status";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String tokenStatus = resolveTokenStatus(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(AUTH_STATUS_HEADER, tokenStatus);
        objectMapper.writeValue(response.getWriter(), Result.unauthorized(resolveMessage(tokenStatus)));
    }

    private String resolveTokenStatus(HttpServletRequest request) {
        Object tokenStatus = request.getAttribute("auth.tokenStatus");
        if (tokenStatus instanceof String status && !status.isBlank()) {
            return status;
        }
        return "missing";
    }

    private String resolveMessage(String tokenStatus) {
        return switch (tokenStatus) {
            case "expired" -> "Token 已过期，请重新登录";
            case "invalid" -> "Token 无效，请重新登录";
            default -> "请先登录";
        };
    }
}
