package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.email.EmailDeliveryService;
import com.gyeote.gyeote_api.domain.auth.email.EmailVerificationPurpose;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.auth.exception.SignupErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 회원가입 및 비밀번호 재설정에 사용하는 이메일 인증 상태를 Redis에서 관리한다.
 *
 * <p>인증번호 발급 제한과 검증은 Lua로 원자적으로 처리하고, 인증 완료 토큰은
 * Redis GETDEL로 한 번만 소모한다. 이메일 정규화와 입력값 검증, 계정 조회는 호출자가
 * 수행하며 실제 메일 전송은 {@link EmailDeliveryService}에 비동기로 위임한다.</p>
 *
 * <p>Redis 변경은 호출자의 DB 트랜잭션에 참여하지 않는다. 토큰 소모 후 DB 작업이
 * 실패해도 토큰은 복원되지 않으므로 다시 이메일 인증을 받아야 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> ISSUE_CODE = script("redis/issue-email-code.lua");
    private static final DefaultRedisScript<Long> VERIFY_CODE = script("redis/verify-email-code.lua");
    private static final String SIGNUP_TOKEN_PREFIX = "email:signup-token:";
    private static final String RESET_TOKEN_PREFIX = "email:reset-token:";

    private final StringRedisTemplate redisTemplate;
    private final EmailDeliveryService emailDeliveryService;

    /**
     * 발송 제한을 확인하고 6자리 인증번호 저장 및 비동기 메일 전송을 요청한다.
     *
     * <p>Lua에서 용도별 재요청 대기 60초와 이메일별 일일 요청 상한 10회를 확인한다.
     * 일일 카운터는 한국 시간 날짜를 기준으로 가입과 재설정이 공유하며, 최초 증가 시
     * 24시간 TTL을 설정한다. 허용된 요청은 기존 코드와 실패 횟수를 지우고,
     * 발송 대상이면 새 코드와 실패 횟수 0을 5분간 저장한다.</p>
     *
     * <p>계정이 일치하지 않는 재설정 요청도 같은 제한을 적용하지만 새 코드를 저장하거나
     * 발송하지 않는다. 발송 작업을 등록한 뒤 SMTP가 실패해도 이 메서드의 응답에는
     * 반영되지 않는다. 큐 거절 시에도 이미 적용한 Redis 상태는 복원하지 않는다.</p>
     *
     * @param email 정규화 및 형식 검증을 마친 이메일
     * @param purpose 회원가입({@code SIGNUP}) 또는 비밀번호 재설정({@code PASSWORD_RESET})
     * @param eligible 발송 대상 여부. 가입은 중복 검사 통과 시, 재설정은 활성 계정의
     *                 아이디와 이메일이 일치할 때 true이며 로그인 여부를 뜻하지 않는다.
     * @throws BusinessException 재요청 대기시간 미충족, 일일 한도 초과 또는 가입 메일 큐 거절 시
     * @throws IllegalStateException 발급 스크립트가 정의되지 않은 결과를 반환한 경우
     */
    public void requestCode(String email, EmailVerificationPurpose purpose, boolean eligible) {
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String day = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
        // issue-email-code.lua에서 제한 검사와 코드 저장을 원자적으로 처리한다.
        Long result = redisTemplate.execute(ISSUE_CODE, List.of(
                "email:cooldown:" + purpose + ":" + email,              // KEYS[1]
                "email:daily:" + email + ":" + day,                     // KEYS[2]
                codeKey(purpose, email),                                // KEYS[3]
                attemptsKey(purpose, email)                             // KEYS[4]
        ), "60", "10", "86400", "300", eligible ? code : "");       // ARGV[1~5]
        if (Long.valueOf(-1).equals(result)) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_COOLDOWN);
        }
        if (Long.valueOf(-2).equals(result)) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_DAILY_LIMIT);
        }
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Unexpected email issuance result");
        }
        if (eligible) { // 발송 대상이면 해당 용도의 인증 메일 전송을 요청한다.
            try {
                emailDeliveryService.send(email, code, purpose);
            } catch (TaskRejectedException e) {
                // 재설정 요청은 큐 상태에 따라 계정 존재 여부를 노출하지 않는다.
                if (purpose == EmailVerificationPurpose.SIGNUP) {
                    throw new BusinessException(AuthErrorCode.AUTH_EMAIL_DELIVERY_UNAVAILABLE);
                }
            }
        }
    }

    /**
     * 가입 인증번호를 검증하고 해당 이메일에 연결된 일회용 가입 토큰을 발급한다.
     *
     * @param email 정규화 및 형식 검증을 마친 이메일
     * @param code 사용자가 제출한 6자리 인증번호
     * @return 10분간 유효하며 가입 시 한 번만 사용할 수 있는 UUID 문자열
     * @throws BusinessException 인증번호가 없거나 만료되었거나 일치하지 않는 경우
     */
    public String verifySignup(String email, String code) {
        return verify(email, code, EmailVerificationPurpose.SIGNUP, email, SIGNUP_TOKEN_PREFIX);
    }

    /**
     * 재설정 인증번호를 검증하고 변경 대상 사용자 ID에 연결된 일회용 토큰을 발급한다.
     *
     * <p>이 메서드는 계정을 조회하지 않는다. 호출자가 활성 계정의 아이디와 이메일이
     * 일치하는지 확인한 뒤 그 계정의 ID를 전달해야 한다.</p>
     *
     * @param email 정규화 및 형식 검증을 마친 이메일
     * @param code 사용자가 제출한 6자리 인증번호
     * @param userId 호출자가 재설정 대상으로 확인한 사용자 ID
     * @return 10분간 유효한 일회용 재설정 토큰의 UUID 문자열
     * @throws BusinessException 인증번호가 없거나 만료되었거나 일치하지 않는 경우
     */
    public String verifyPasswordReset(String email, String code, Long userId) {
        return verify(email, code, EmailVerificationPurpose.PASSWORD_RESET, userId.toString(), RESET_TOKEN_PREFIX);
    }

    /**
     * 용도별 코드 검증과 완료 토큰 저장을 하나의 Lua 연산으로 처리한다.
     *
     * <p>불일치 시 실패 횟수를 증가시키고 5회 도달 시 코드와 실패 횟수를 삭제한다.
     * 일치 시 완료 토큰을 600초 동안 저장하고 코드와 실패 횟수를 삭제한다.
     * UUID 생성 자체는 Java에서 수행하며, 검증 성공 시에만 Redis에 저장한다.</p>
     *
     * @param email 정규화된 이메일
     * @param code 검증할 인증번호
     * @param purpose 인증번호를 발급한 용도
     * @param payload 완료 토큰의 값으로 저장할 이메일 또는 사용자 ID 문자열
     * @param tokenPrefix 완료 토큰 Redis 키의 접두사
     * @return 검증에 성공해 Redis에 저장한 토큰 문자열
     * @throws BusinessException 검증 스크립트 결과가 성공 코드 1이 아닌 경우
     */
    private String verify(String email, String code, EmailVerificationPurpose purpose,
                          String payload, String tokenPrefix) {
        String token = UUID.randomUUID().toString();
        Long result = redisTemplate.execute(VERIFY_CODE,
                List.of(codeKey(purpose, email), attemptsKey(purpose, email), tokenPrefix + token),
                code, payload, "600", "5");
        if (!Long.valueOf(1).equals(result)) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_EMAIL_CODE);
        }
        return token;
    }

    /**
     * 가입 토큰을 GETDEL로 조회·삭제하고 인증된 이메일과 가입 이메일을 비교한다.
     *
     * <p>이메일이 다르더라도 토큰은 이미 소모된 상태다. 이후 가입 DB 트랜잭션이
     * 롤백되어도 삭제한 Redis 토큰은 복원되지 않는다.</p>
     *
     * @param token 가입 요청에 제출된 일회용 이메일 인증 토큰
     * @param email 정규화된 가입 이메일
     * @throws BusinessException 토큰이 없거나 만료·소모되었거나 이메일이 일치하지 않는 경우
     */
    public void consumeSignupToken(String token, String email) {
        String verifiedEmail = redisTemplate.opsForValue().getAndDelete(SIGNUP_TOKEN_PREFIX + token);
        if (!email.equals(verifiedEmail)) {
            throw new BusinessException(SignupErrorCode.SIGNUP_EMAIL_NOT_VERIFIED);
        }
    }

    /**
     * 재설정 토큰을 GETDEL로 한 번만 소모하고 연결된 사용자 ID를 반환한다.
     *
     * <p>사용자 존재·활성 상태는 호출자가 확인한다. 이후 DB 변경이 실패하더라도
     * 소모한 토큰은 복원되지 않는다.</p>
     *
     * @param token 새 비밀번호 설정 요청에 제출된 일회용 토큰
     * @return 토큰 발급 시 저장한 변경 대상 사용자 ID
     * @throws BusinessException 토큰이 없거나 만료되었거나 이미 소모된 경우
     * @throws NumberFormatException Redis에 저장된 값이 Long 형식의 사용자 ID가 아닌 경우
     */
    public Long consumeResetToken(String token) {
        String userId = redisTemplate.opsForValue().getAndDelete(RESET_TOKEN_PREFIX + token);
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_RESET_TOKEN);
        }
        return Long.valueOf(userId);
    }

    /**
     * 발급과 검증에서 공유하는 인증번호 저장 키를 구성한다. 토큰을 생성하지는 않는다.
     *
     * @param purpose 인증 용도
     * @param email 정규화된 이메일
     * @return {@code email:code:{purpose}:{email}} 형태의 Redis 키
     */
    private String codeKey(EmailVerificationPurpose purpose, String email) {
        return "email:code:" + purpose + ":" + email;
    }

    /**
     * 인증번호 검증 실패 횟수를 저장할 Redis 키를 구성한다.
     *
     * @param purpose 인증 용도
     * @param email 정규화된 이메일
     * @return {@code email:attempts:{purpose}:{email}} 형태의 Redis 키
     */
    private String attemptsKey(EmailVerificationPurpose purpose, String email) {
        return "email:attempts:" + purpose + ":" + email;
    }

    /**
     * 클래스패스 Lua 파일의 위치와 정수 결과 타입을 지정한 실행 설정 객체를 만든다.
     *
     * <p>이 메서드는 스크립트를 실행하지 않는다. 실제 실행은
     * {@code redisTemplate.execute(...)} 호출 시 Redis에서 이루어진다.
     * Redis 정수 응답에 대응하도록 결과 타입을 {@link Long}으로 지정한다.</p>
     *
     * @param path {@code src/main/resources} 기준 Lua 파일 경로
     * @return Lua 파일 위치와 Long 반환 타입이 설정된 스크립트 객체
     */
    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
