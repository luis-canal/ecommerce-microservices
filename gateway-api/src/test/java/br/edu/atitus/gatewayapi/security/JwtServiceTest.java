package br.edu.atitus.gatewayapi.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET = "test-signing-secret-longer-than-32-bytes";

    @Test
    void parsesRequiredJtiAndMapsLegacyRolesWithAuthEnumOrdering() {
        JwtService service = new JwtService(SECRET);
        String token = token(1);

        JwtClaims claims = service.parse(token).orElseThrow();

        assertEquals("42", claims.userId());
        assertEquals("CLIENTE", claims.type());
        assertEquals("client@example.test", claims.email());
        assertTrue(claims.tokenId().length() > 0);
    }

    @Test
    void rejectsTokenWithoutJtiAndUnknownRole() {
        JwtService service = new JwtService(SECRET);
        String noJti = Jwts.builder().claim("id", 42).claim("type", "CLIENTE")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        String unknownRole = token("UNKNOWN");

        assertTrue(service.parse(noJti).isEmpty());
        assertTrue(service.parse(unknownRole).isEmpty());
    }

    private static String token(Object type) {
        return Jwts.builder().id(UUID.randomUUID().toString())
                .claim("id", 42).claim("type", type).claim("email", "client@example.test")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}