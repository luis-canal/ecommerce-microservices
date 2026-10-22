package br.edu.atitus.authapi.components;

import br.edu.atitus.authapi.entities.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtUtilTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-secret-with-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8));

    @Test
    void emitsTextualRoleAndUniqueTokenId() {
        JwtUtil jwtUtil = new JwtUtil(SECRET);
        String token = jwtUtil.generateToken("admin@example.test", 17L, UserType.ADMINISTRADOR);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("ADMINISTRADOR", claims.get("type", String.class));
        assertEquals(17, claims.get("id", Integer.class));
        assertEquals("admin@example.test", claims.get("email", String.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void emitsClientRoleAsText() {
        JwtUtil jwtUtil = new JwtUtil(SECRET);
        String token = jwtUtil.generateToken("client@example.test", 18L, UserType.CLIENTE);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("CLIENTE", claims.get("type", String.class));
    }
}