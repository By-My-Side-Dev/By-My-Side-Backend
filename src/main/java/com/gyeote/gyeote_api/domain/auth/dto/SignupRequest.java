package com.gyeote.gyeote_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @NotBlank
        @Size(max = 50)
        String loginId,

        @NotBlank(message = "SIGNUP_REQUIRED_PASSWORD")
        @Size(min = 8, message = "SIGNUP_REQUIRED_PASSWORD")
        String password,

        @NotBlank(message = "SIGNUP_REQUIRED_PHONE")
        String phone
) {
}
