package com.gyeote.gyeote_api.global.exception;

import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스에서 의도적으로 던진 비즈니스 예외 처리.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * DTO 검증(@NotBlank, @Pattern 등) 실패 처리.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String codeName = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();

        ErrorCode errorCode;
        try {
            errorCode = AuthErrorCode.valueOf(codeName);
        } catch (IllegalArgumentException | NullPointerException ex) {
            errorCode = CommonErrorCode.COMMON_VALIDATION_FAILED;
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * 예상하지 못한 모든 예외 처리.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("COMMON_INTERNAL_SERVER_ERROR", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR));
    }
}
