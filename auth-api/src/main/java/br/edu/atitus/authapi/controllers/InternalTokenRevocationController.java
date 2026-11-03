package br.edu.atitus.authapi.controllers;

import br.edu.atitus.authapi.services.TokenRevocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/auth/token-revocation")
public class InternalTokenRevocationController {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final TokenRevocationService tokenRevocationService;
    private final byte[] internalSecret;

    public InternalTokenRevocationController(
            TokenRevocationService tokenRevocationService,
            @Value("${app.auth.internal-secret}") String internalSecret) {
        if (internalSecret == null || internalSecret.length() < 32) {
            throw new IllegalArgumentException("app.auth.internal-secret must contain at least 32 characters");
        }
        this.tokenRevocationService = tokenRevocationService;
        this.internalSecret = internalSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/check")
    public ResponseEntity<Boolean> checkRevocation(
            @RequestHeader(value = INTERNAL_AUTH_HEADER, required = false) String providedSecret,
            @RequestBody TokenHashRequest request) {
        if (providedSecret == null || !MessageDigest.isEqual(
                internalSecret, providedSecret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.tokenHash() == null || !request.tokenHash().matches("[0-9a-f]{64}")) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(tokenRevocationService.isRevoked(request.tokenHash()));
    }

    public record TokenHashRequest(String tokenHash) {
    }
}
