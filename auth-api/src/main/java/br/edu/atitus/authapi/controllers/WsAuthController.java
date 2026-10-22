package br.edu.atitus.authapi.controllers;

import br.edu.atitus.commons.context.UserContext;
import br.edu.atitus.commons.context.UserContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ws/auth")
public class WsAuthController {
    @GetMapping("/me")
    public UserContext me() {
        return UserContextHolder.require();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Revogação persistente será implementada junto ao armazenamento de tokens.
        return ResponseEntity.noContent().build();
    }
}