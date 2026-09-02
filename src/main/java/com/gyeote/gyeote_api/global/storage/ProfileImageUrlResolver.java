package com.gyeote.gyeote_api.global.storage;

import com.gyeote.gyeote_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImageUrlResolver {

    private final ObjectStorage objectStorage;

    public String resolve(User user) {
        String key = user.getProfileImageKey();

        return key == null ? null : objectStorage.getUrl(key).toString();
    }
}
