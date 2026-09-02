package com.gyeote.gyeote_api.domain.user.dto;

import org.springframework.web.multipart.MultipartFile;

public record ProfileImageRequest(
        MultipartFile image
) {
}