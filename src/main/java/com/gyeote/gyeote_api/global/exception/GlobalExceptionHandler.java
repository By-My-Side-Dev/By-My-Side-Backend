package com.gyeote.gyeote_api.global.exception;

import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.auth.exception.SignupErrorCode;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스에서 의도적으로 던진 비즈니스 예외 처리.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return error(e.getErrorCode());
    }

    /**
     * DTO 검증(@NotBlank, @Pattern 등) 실패 처리.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String codeName = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(null);
        ErrorCode errorCode = resolveErrorCode(codeName);

        return error(errorCode);
    }

    /**
     * JSON 파싱 실패, enum 변환 실패 등 요청 본문 형식 오류 처리.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException e) {
        return error(CommonErrorCode.COMMON_INVALID_REQUEST);
    }

    /**
     * 존재하지 않는 URL 요청 처리.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        return error(CommonErrorCode.COMMON_NOT_FOUND);
    }

    /**
     * 존재하는 URL에 지원하지 않는 HTTP method로 요청한 경우 처리.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED);
    }

    /**
     * Content-Type이 맞지 않는 요청 처리.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return error(CommonErrorCode.COMMON_UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * query/path parameter 누락, 타입 변환 실패, 제약 조건 위반 처리.
     */
    @ExceptionHandler({
            BindException.class,
            MissingPathVariableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestValue(Exception e) {
        return error(CommonErrorCode.COMMON_INVALID_REQUEST);
    }

    /**
     * DTO 검증 message에 적힌 에러 코드 이름을 도메인 enum에서 찾는다. 없으면 공통 검증 실패로 폴백.
     */
    private ErrorCode resolveErrorCode(String codeName) {
        if (codeName == null) {
            return CommonErrorCode.COMMON_VALIDATION_FAILED;
        }
        try {
            return AuthErrorCode.valueOf(codeName);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return SignupErrorCode.valueOf(codeName);
        } catch (IllegalArgumentException ignored) {
        }
        return CommonErrorCode.COMMON_VALIDATION_FAILED;
    }

    /**
     * 예상하지 못한 모든 예외 처리.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("COMMON_INTERNAL_SERVER_ERROR", e);
        return error(CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }
}
