package br.edu.atitus.authapi.controllers;

import br.edu.atitus.authapi.components.JwtUtil;
import br.edu.atitus.authapi.dtos.SigninRequestDto;
import br.edu.atitus.authapi.dtos.SigninResponseDto;
import br.edu.atitus.authapi.dtos.SignupRequestDto;
import br.edu.atitus.authapi.entities.UserEntity;
import br.edu.atitus.authapi.entities.UserType;
import br.edu.atitus.authapi.services.TokenRevocationService;
import br.edu.atitus.authapi.services.UserService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.AuthenticationException;

@RestController
@RequestMapping({"/auth", "/ws/auth"})
public class AuthController {

    private final UserService userService;
    private final AuthenticationConfiguration authConfig;
    private final TokenRevocationService tokenRevocationService;

    public AuthController(UserService userService, AuthenticationConfiguration authConfig,
                          TokenRevocationService tokenRevocationService) {
        this.userService = userService;
        this.authConfig = authConfig;
        this.tokenRevocationService = tokenRevocationService;
    }

    private UserEntity convertDTO2Entity(SignupRequestDto dto) {
        var user = new UserEntity();
        BeanUtils.copyProperties(dto, user);
        return user;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserEntity> signup(@RequestBody SignupRequestDto dto) throws Exception {
        var user = convertDTO2Entity(dto);
        user.setType(UserType.Common);
        userService.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    @PostMapping("/signin")
    public ResponseEntity<SigninResponseDto> PostSignin(@RequestBody SigninRequestDto signin) throws AuthenticationException, Exception {
        authConfig.getAuthenticationManager()
                .authenticate(new UsernamePasswordAuthenticationToken(signin.email(), signin.password()));
        UserEntity user = (UserEntity) userService.loadUserByUsername(signin.email());
        SigninResponseDto response = new SigninResponseDto(user, JwtUtil.generateToken(user.getEmail(), user.getId(), user.getType()));
        return ResponseEntity.ok(response);

    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Claims claims = JwtUtil.validateToken(token);
        if (claims == null || claims.getExpiration() == null
                || !claims.getExpiration().toInstant().isAfter(java.time.Instant.now())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        tokenRevocationService.revoke(token, claims.getExpiration().toInstant());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        String cleanMessage = e.getMessage().replaceAll("[\\r\\n]", " ");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cleanMessage);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleException(AuthenticationException e) {
        String cleanMessage = e.getMessage().replaceAll("[\\r\\n]", " ");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(cleanMessage);
    }
}
