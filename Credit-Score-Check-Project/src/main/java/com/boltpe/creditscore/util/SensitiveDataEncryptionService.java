package com.boltpe.creditscore.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SensitiveDataEncryptionService {

    private static final String PREFIX = "enc:v1:";

    // 32 hexadecimal characters = 16 bytes = AES-128
    private static final int KEY_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveDataEncryptionService(
            @Value("${credit-score.data-encryption.key:}") String encodedKey) {

        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }

        String hexKey = encodedKey.trim();

        if (!hexKey.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException(
                    "Credit-score data encryption key must contain exactly 32 hexadecimal characters"
            );
        }

        byte[] decoded = HexFormat.of().parseHex(hexKey);

        if (decoded.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Credit-score data encryption key must decode to 16 bytes"
            );
        }

        this.key = decoded;
    }

    public String encrypt(String plainText) {

        if (plainText == null) {
            return null;
        }

        requireKey();

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            byte[] cipherText = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer payload = ByteBuffer.allocate(
                    iv.length + cipherText.length
            );

            payload.put(iv).put(cipherText);

            return PREFIX + Base64.getEncoder()
                    .encodeToString(payload.array());

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Unable to encrypt credit-score data", ex
            );
        }
    }

    public String decrypt(String value) {

        if (value == null || !value.startsWith(PREFIX)) {
            return value;
        }

        requireKey();

        try {
            byte[] payload = Base64.getDecoder().decode(
                    value.substring(PREFIX.length())
            );

            if (payload.length < IV_BYTES + 16) {
                throw new IllegalArgumentException(
                        "Invalid encrypted credit-score data"
                );
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);

            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            return new String(
                    cipher.doFinal(cipherText),
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Unable to decrypt credit-score data", ex
            );
        }
    }

    private void requireKey() {

        if (key == null) {
            throw new IllegalStateException(
                    "Credit-score data encryption key is not configured"
            );
        }
    }
}