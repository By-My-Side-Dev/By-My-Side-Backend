package com.gyeote.gyeote_api.global.storage;

import java.net.URI;

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
     * 객체에 접근할 수 있는 URL을 생성한다.
     * URL의 유효 기간은 구현체 내부 정책을 따른다.
     *
     * @param objectKey URL을 생성할 객체를 식별하는 키
     * @return 객체 접근 URL
     */
    URI getUrl(String objectKey);
}
