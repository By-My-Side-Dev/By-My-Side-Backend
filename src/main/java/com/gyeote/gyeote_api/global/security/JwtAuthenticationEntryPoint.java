package com.gyeote.gyeote_api.global.security;

import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import com.gyeote.gyeote_api.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 없이 보호된 API에 접근했을 때 공통 응답 포맷으로 401을 내려준다.
 * 필터가 토큰 실패 사유(만료/무효)를 request attribute에 남겼으면 그 코드로 응답한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)
                instanceof ErrorCode tokenError ? tokenError : CommonErrorCode.COMMON_UNAUTHORIZED;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(toErrorJson(errorCode));
    }

    private String toErrorJson(ErrorCode errorCode) {
        return "{\"code\":\"%s\",\"message\":\"%s\",\"data\":null}"
                .formatted(escapeJson(errorCode.getCode()), escapeJson(errorCode.getMessage()));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
