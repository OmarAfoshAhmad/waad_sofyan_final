package com.waad.tba.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretEncryptionService {

    static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(
            @Value("${app.security.secrets.encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey == null ? "" : encodedKey.trim();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt application secret", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        if (!isEncrypted(storedValue)) {
            throw new IllegalStateException("Unencrypted application secret is not permitted");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalStateException("Invalid encrypted application secret");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[payload.length - NONCE_BYTES];
            System.arraycopy(payload, 0, nonce, 0, nonce.length);
            System.arraycopy(payload, nonce.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt application secret", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private SecretKeySpec key() {
        if (encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "APP_SECRET_ENCRYPTION_KEY must be configured before email credentials can be used");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encodedKey);
            if (raw.length != 32) {
                throw new IllegalStateException("APP_SECRET_ENCRYPTION_KEY must decode to exactly 32 bytes");
            }
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_SECRET_ENCRYPTION_KEY must be valid Base64", e);
        }
    }
}
