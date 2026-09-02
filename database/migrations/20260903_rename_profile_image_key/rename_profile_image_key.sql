-- 프로필 이미지 컬럼의 이름을 실제 저장 값인 Azure Blob object key에 맞게 변경한다.
ALTER TABLE users
    RENAME COLUMN profile_image_url TO profile_image_key;

-- 기존 Azure Blob URL은 컨테이너 경로와 SAS 쿼리를 제거하고 object key만 보존한다.
-- 예: https://account.blob.core.windows.net/container/profile/user/image.jpg?...
--  -> profile/user/image.jpg
UPDATE users
SET profile_image_key = REGEXP_REPLACE(
        SUBSTRING_INDEX(profile_image_key, '?', 1),
        '^https?://[^/]+/[^/]+/',
        ''
    )
WHERE profile_image_key REGEXP
      '^https?://[^/]+[.]blob[.]core[.]windows[.]net/[^/]+/.+';
