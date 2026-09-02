package com.gyeote.gyeote_api.global.storage;

import java.net.URI;
import java.time.Duration;

public interface ObjectStorage{

    /**
     * 요청에 포함된 객체를 스토리지에 업로드한다.
     *
     * @param request 객체 키, 콘텐츠 유형 및 데이터 스트림을 포함한 업로드 요청
     */
    void upload(ObjectUploadRequest request);

    /**
     * 지정한 객체 키에 해당하는 객체를 스토리지에서 삭제한다.
     *
     * @param objectKey 삭제할 객체를 식별하는 키
     */
    void delete(String objectKey);

    /**
     * 지정한 시간 동안 객체에 접근할 수 있는 URL을 생성한다.
     *
     * @param objectKey URL을 생성할 객체를 식별하는 키
     * @param ttl URL의 유효 기간
     * @return 유효 기간이 설정된 객체 접근 URL
     * @throws IllegalArgumentException 유효 기간이 허용되지 않는 값인 경우
     */
    URI getUrl(String objectKey, Duration ttl);
}
