package id.ac.ui.cs.advprog.bidmart.auction.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Mengekstrak identitas pengguna (email) dari token JWT.
     * Sesuai dengan service Auth, identitas disimpan dalam claim 'sub' (subject).
     */
    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        if (isTokenExpired(claims)) {
            throw new IllegalStateException("Token has expired");
        }
        return claims.getSubject();
    }

    private Claims extractAllClaims(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new java.util.Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
