package br.edu.atitus.authapi.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsSafeAuthenticationMessageInsteadOfSecurityDetails() {
        var response = handler.handleAuthentication(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
        assertThat(response.getBody()).isEqualTo("E-mail ou senha inválidos.");
    }

    @Test
    void preservesConflictStatusAndDomainMessage() {
        var response = handler.handleResponseStatus(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Já existe um usuário com este e-mail."
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo("Já existe um usuário com este e-mail.");
    }
}
