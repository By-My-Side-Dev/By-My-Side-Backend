package com.gyeote.gyeote_api.domain.contact.dto;

import jakarta.validation.constraints.NotBlank;

public record UserSearchRequest(
        @NotBlank String loginId
) {
}
