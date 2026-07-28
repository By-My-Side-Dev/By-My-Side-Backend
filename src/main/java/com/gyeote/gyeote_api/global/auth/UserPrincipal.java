package com.gyeote.gyeote_api.global.auth;

/**
 * Spring Security {@code Authentivation}의 principal로 사용되는 로그인 유저 정보</br>
 * 컨트롤러에서 {@code @AuthenticationPrincipal UserPrincipal principal}로 주입받는다.</br>
 * @param id 테스트할 유저 아이디
 */
public record UserPrincipal(Long id) {
}
