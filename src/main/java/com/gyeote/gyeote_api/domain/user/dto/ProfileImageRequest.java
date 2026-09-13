package com.gyeote.gyeote_api.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ProfileImageRequest(
        @NotNull(message = "USER_PROFILE_IMAGE_EMPTY")
        @Schema(type = "string", format = "binary", description = "업로드할 JPEG 또는 PNG 이미지 파일")
        MultipartFile image
) {
}
