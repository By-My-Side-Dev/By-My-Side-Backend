package com.gyeote.gyeote_api.domain.auth.dto;

import com.gyeote.gyeote_api.domain.auth.email.EmailAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SignupEmailCodeRequest(
        @Schema(example = "user@example.com")
        @NotBlank @Email @Size(max = 255) String email
) {
    public SignupEmailCodeRequest {
        email = EmailAddress.normalize(email);
    }
}
