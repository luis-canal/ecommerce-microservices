package br.edu.atitus.authapi.components;

import br.edu.atitus.authapi.entities.UserType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    private static final long EXPIRATION_TIME = 1000L * 60 * 60 * 24;

    private final SecretKey signingKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException invalidBase64) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret deve conter ao menos 256 bits");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, Long id, UserType type) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("id", id)
                .claim("email", email)
                .claim("type", type.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRATION_TIME))
                .signWith(signingKey)
                .compact();
    }
}
