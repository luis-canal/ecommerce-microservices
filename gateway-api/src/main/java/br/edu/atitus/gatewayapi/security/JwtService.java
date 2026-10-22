package br.edu.atitus.gatewayapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtService {
    private final SecretKey signingKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
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

    public Optional<JwtClaims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())
                    || claims.getId() == null || claims.getId().isBlank()) {
                return Optional.empty();
            }

            String id = scalar(claims.get("id"));
            String type = role(claims.get("type"));
            String email = scalar(claims.get("email"));
            if (id == null || type == null) {
                return Optional.empty();
            }
            return Optional.of(new JwtClaims(id, type, email, claims.getId()));
        } catch (Exception invalidToken) {
            return Optional.empty();
        }
    }

    private static String scalar(Object value) {
        return value instanceof String || value instanceof Number ? value.toString() : null;
    }

    private static String role(Object rawType) {
        if (rawType instanceof String type) {
            return switch (type) {
                case "CLIENTE", "ADMINISTRADOR" -> type;
                default -> null;
            };
        }
        if (rawType instanceof Number ordinal) {
            // Legacy UserType enum order in auth-api is Admin=0, Common=1.
            return switch (ordinal.intValue()) {
                case 0 -> "ADMINISTRADOR";
                case 1 -> "CLIENTE";
                default -> null;
            };
        }
        return null;
    }
}