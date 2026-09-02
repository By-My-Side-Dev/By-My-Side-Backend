package com.gyeote.gyeote_api.global.storage;

import java.io.InputStream;

public record ObjectUploadRequest(
        String objectKey,
        InputStream contentStream,
        String contentType
) {
    public static ObjectUploadRequest from(
            String objectKey,
            InputStream contentStream,
            String contentType
    ) {
        return new ObjectUploadRequest(
                objectKey,
                contentStream,
                contentType
        );
    }
}
