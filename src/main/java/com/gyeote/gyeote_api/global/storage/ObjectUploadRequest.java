package com.gyeote.gyeote_api.global.storage;

import java.io.InputStream;

public record ObjectUploadRequest(
        String objectKey,
        InputStream contentStream,
        String contentType
) {
}
