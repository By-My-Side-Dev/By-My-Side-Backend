package com.gyeote.gyeote_api.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class DeviceTokenCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceTokenCryptoService(@Value("${device-token.enc-key:}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            this.encryptionKey = null;
            return;
        }
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalArgumentException("device-token.enc-key는 16, 24 또는 32바이트 크기의 Base64로 인코딩된 AES 키여야 합니다.");
        }
        this.encryptionKey = new SecretKeySpec(decoded, "AES");
    }

    public boolean isConfigured() {
        return encryptionKey != null;
    }

    public byte[] encrypt(String token) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("기기 토큰 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(byte[] encrypted) {
        requireConfigured();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            if (buffer.remaining() <= IV_LENGTH) {
                throw new IllegalArgumentException("암호화된 장치 토큰이 잘못되었습니다.");
            }
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("장치 토큰 복호화에 실패했습니다.", e);
        }
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private void requireConfigured() {
        if (encryptionKey == null) {
            throw new IllegalStateException("FCM_TOKEN_ENC_KEY를 구성해야 합니다.");
        }
    }
}
