package de.savas.businessinsightapi.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTripsAndUsesAFreshIvEachTime() {
        SecretCipher cipher = new SecretCipher(KEY);
        String a = cipher.encrypt("s3cr3t-pässwörd");
        String b = cipher.encrypt("s3cr3t-pässwörd");
        assertThat(a).startsWith(SecretCipher.PREFIX).doesNotContain("s3cr3t").isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("s3cr3t-pässwörd");
        assertThat(cipher.decrypt(b)).isEqualTo("s3cr3t-pässwörd");
    }

    @Test
    void detectsTamperingAndWrongKeys() {
        String stored = new SecretCipher(KEY).encrypt("value");
        char last = stored.charAt(stored.length() - 3);
        String tampered = stored.substring(0, stored.length() - 3) + (last == 'A' ? 'B' : 'A') + stored.substring(stored.length() - 2);

        assertThatThrownBy(() -> new SecretCipher(KEY).decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(stored))
                .hasMessageContaining("cannot be decrypted with the configured key");
    }

    @Test
    void isDisabledWithoutKeyAndRejectsBadKeys() {
        SecretCipher disabled = new SecretCipher("");
        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.encrypt("x")).hasMessageContaining("APP_SECRETS_KEY");
        assertThatThrownBy(() -> new SecretCipher("not base64!")).hasMessageContaining("base64");
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16]))).hasMessageContaining("32 bytes");
    }
}
