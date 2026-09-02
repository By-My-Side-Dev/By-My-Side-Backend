package com.gyeote.gyeote_api.domain.user.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ProfileImageRequest(
        @NotNull(message = "USER_PROFILE_IMAGE_EMPTY")
        MultipartFile image
) {
}
