package com.gyeote.gyeote_api.global.storage;

import java.net.URI;
import java.time.Duration;

public interface ObjectStorage{

    void upload(ObjectUploadRequest request);

    void delete(String objectKey);

    URI getUrl(String objectKey, Duration ttl);
}
