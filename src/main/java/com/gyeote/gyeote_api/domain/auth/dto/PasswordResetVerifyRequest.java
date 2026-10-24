package com.gyeote.gyeote_api.domain.auth.dto;

import com.gyeote.gyeote_api.domain.auth.email.EmailAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetVerifyRequest(
        @Schema(example = "id1234") @NotBlank @Size(max = 50) String loginId,
        @Schema(example = "user@example.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "123456") @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    public PasswordResetVerifyRequest {
        email = EmailAddress.normalize(email);
    }
}
