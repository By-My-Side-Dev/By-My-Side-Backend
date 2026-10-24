package com.gyeote.gyeote_api.domain.auth.dto;

import com.gyeote.gyeote_api.domain.auth.email.EmailAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SignupEmailVerifyRequest(
        @Schema(example = "user@example.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "6자리 인증번호", example = "123456")
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    public SignupEmailVerifyRequest {
        email = EmailAddress.normalize(email);
    }
}
