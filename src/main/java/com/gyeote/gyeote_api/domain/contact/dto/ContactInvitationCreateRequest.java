package com.gyeote.gyeote_api.domain.contact.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ContactInvitationCreateRequest(
        @NotNull
        @Positive
        Long receiverId
) {
}
