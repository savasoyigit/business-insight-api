package de.savas.businessinsightapi.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts datasource passwords at rest with AES-256-GCM.
 * Stored form: {@code enc:v1:<base64(iv || ciphertext+tag)>}. The version prefix leaves room for key rotation.
 * The key comes from {@code APP_SECRETS_KEY} (base64, 32 bytes); without it, encrypted secrets are disabled.
 */
@Component
public class SecretCipher {

    public static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.security.secrets-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_SECRETS_KEY must be base64 encoded");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("APP_SECRETS_KEY must decode to 32 bytes (AES-256), got " + bytes.length);
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public boolean isEnabled() {
        return key != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Secret encryption failed", ex);
        }
    }

    public String decrypt(String stored) {
        requireKey();
        if (!isEncrypted(stored)) {
            throw new IllegalArgumentException("Not an encrypted secret");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (raw.length <= IV_BYTES) {
                throw new IllegalStateException("Encrypted secret is malformed");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new IllegalStateException("Encrypted secret cannot be decrypted with the configured key");
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Encrypted secret is malformed");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Encrypted secrets are disabled: set APP_SECRETS_KEY");
        }
    }
}
