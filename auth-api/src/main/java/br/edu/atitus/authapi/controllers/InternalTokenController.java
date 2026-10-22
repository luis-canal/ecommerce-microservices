package br.edu.atitus.authapi.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tokens")
public class InternalTokenController {
    @GetMapping("/{jti}/revoked")
    public boolean isRevoked(@PathVariable String jti) {
        // Stub para integrar o gateway antes da implementação da lista persistente.
        return false;
    }
}