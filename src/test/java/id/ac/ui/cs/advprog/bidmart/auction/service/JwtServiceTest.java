package id.ac.ui.cs.advprog.bidmart.auction.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "your_jwt_secret_key_at_least_256_bits_length_for_security";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", secret);
    }

    private String createToken(String subject, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void testExtractUserIdSuccess() {
        String token = createToken("user@mail.com", 3600000);
        String result = jwtService.extractUserId("Bearer " + token);
        assertEquals("user@mail.com", result);
    }

    @Test
    void testExtractUserIdWithoutBearerPrefix() {
        String token = createToken("user@mail.com", 3600000);
        String result = jwtService.extractUserId(token);
        assertEquals("user@mail.com", result);
    }

    @Test
    void testExtractUserIdExpired() {
        // Token expired 1 hour ago
        String token = createToken("user@mail.com", -3600000);
        assertThrows(IllegalStateException.class, () -> {
            jwtService.extractUserId("Bearer " + token);
        });
    }

    @Test
    void testExtractUserIdInvalidSignature() {
        String token = createToken("user@mail.com", 3600000);
        // Tamper with the token
        String tamperedToken = token + "xyz";
        assertThrows(Exception.class, () -> {
            jwtService.extractUserId("Bearer " + tamperedToken);
        });
    }
}
