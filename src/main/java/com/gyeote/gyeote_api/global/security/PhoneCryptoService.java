package com.gyeote.gyeote_api.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 전화번호 암호화 모듈 (DB 명세서 v1.1.0 — 3.2 user_phones).
 * - phone_encrypted: AES-GCM 랜덤 IV 암호값(복호화용). 반환 형식 = IV(12바이트) || 암호문
 * - phone_hash: HMAC_SHA256(secret + pepper, 정규화 번호) 블라인드 인덱스(조회·매칭용).
 *   pepper로 전화번호처럼 경우의 수가 한정된 값의 사전 대입 공격을 방지한다.
 * 키·secret·pepper는 코드·DB에 두지 않고 환경변수로 주입한다.
 */
@Component
public class PhoneCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKey encKey;
    private final byte[] hmacKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PhoneCryptoService(
            @Value("${phone.enc-key}") String encKeyBase64,
            @Value("${phone.hmac-secret}") String hmacSecret,
            @Value("${phone.pepper}") String pepper
    ) {
        byte[] decodedKey = Base64.getDecoder().decode(encKeyBase64);
        if (decodedKey.length != 16 && decodedKey.length != 24 && decodedKey.length != 32) {
            throw new IllegalArgumentException("phone.enc-key must be a Base64 encoded AES key of 16, 24, or 32 bytes.");
        }

        this.encKey = new SecretKeySpec(decodedKey, "AES");
        this.hmacKey = (hmacSecret + pepper).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 숫자만 남기는 정규화 (010-1234-5678 → 01012345678).
     * 암호화·해시·Redis 키 조합 전에 반드시 거친다.
     */
    public String normalize(String phone) {
        return phone.replaceAll("\\D", "");
    }

    /**
     * 유저의 전화번호를 암호화 하는 로직</br>
     * AES-GCM 랜덤 IV 암호화. 매번 다른 IV라 같은 번호도 암호값이 달라진다(복호화 전용, 매칭은 hash 사용).
     * @param normalizedPhone 정규화된 전화번호 문자열을 매개변수로 받는다.(normalize() 사용 -> 01012345678)
     * @return 전화번호를 암호화 한 값을 반환
     */
    public byte[] encrypt(String normalizedPhone) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("전화번호 암호화에 실패했습니다.", e);
        }
    }

    /**
     * 유저의 전화번호를 복호화 시키는 로직</br>
     * 16바이트 → AES-128</br>
     * 24바이트 → AES-192</br>
     * 32바이트 → AES-256</br>
     * @param encrypted 암호화된 전화번호를 매개변수로 받는다.
     * @return 암호화된 전화번호를 복호화 해서 반환
     */
    public String decrypt(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("전화번호 복호화에 실패했습니다.", e);
        }
    }

    /**
     * 전화번호 조회·매칭용 블라인드 인덱스(HMAC-SHA256)를 생성하는 로직</br>
     * @param normalizedPhone 정규화된 전화번호 문자열을 매개변수로 받는다.(normalize() 사용 -> 01012345678)
     * @return 64자리 hex 문자열로 인코딩된 HMAC-SHA256 해시값
     */
    public String hash(String normalizedPhone) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("전화번호 해시 생성에 실패했습니다.", e);
        }
    }
}
