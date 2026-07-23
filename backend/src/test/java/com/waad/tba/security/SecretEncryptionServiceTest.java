package com.waad.tba.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretEncryptionServiceTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsWithRandomNonceAndDecryptsWithoutPersistingPlaintext() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);

        String first = service.encrypt("mail-secret");
        String second = service.encrypt("mail-secret");

        assertTrue(service.isEncrypted(first));
        assertNotEquals(first, second);
        assertFalse(first.contains("mail-secret"));
        assertEquals("mail-secret", service.decrypt(first));
        assertEquals("mail-secret", service.decrypt(second));
    }

    @Test
    void rejectsPlaintextAndTamperedCiphertextOnDecrypt() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);
        String encrypted = service.encrypt("mail-secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThrows(IllegalStateException.class, () -> service.decrypt("mail-secret"));
        assertThrows(IllegalStateException.class, () -> service.decrypt(tampered));
    }

    @Test
    void requiresAValid256BitKeyOnlyWhenSecretIsUsed() {
        SecretEncryptionService missing = new SecretEncryptionService("");
        SecretEncryptionService shortKey = new SecretEncryptionService(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertNull(missing.encrypt(null));
        assertThrows(IllegalStateException.class, () -> missing.encrypt("secret"));
        assertThrows(IllegalStateException.class, () -> shortKey.encrypt("secret"));
    }
}
