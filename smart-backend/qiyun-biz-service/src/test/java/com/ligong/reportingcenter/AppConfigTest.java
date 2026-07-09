package com.ligong.reportingcenter;

import com.ligong.reportingcenter.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void passwordEncoder_shouldReturnBCryptPasswordEncoder_andMatchEncoding() {
        AppConfig config = new AppConfig();
        PasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder, "passwordEncoder should not be null");
        assertTrue(encoder instanceof BCryptPasswordEncoder, "passwordEncoder should be a BCryptPasswordEncoder");

        String raw = "SecretPass123!";
        String encoded = encoder.encode(raw);

        assertNotNull(encoded, "encoded password should not be null");
        assertNotEquals(raw, encoded, "encoded password should not equal raw");
        assertTrue(encoder.matches(raw, encoded), "encoder should match raw and encoded password");
    }
}