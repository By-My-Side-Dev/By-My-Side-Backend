package com.gyeote.gyeote_api.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserProfileUpdateRequest(
        @NotBlank
        String name,

        @NotBlank
        String phoneNumber
) {
}
