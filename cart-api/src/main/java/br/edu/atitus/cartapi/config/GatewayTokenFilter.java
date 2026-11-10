package br.edu.atitus.cartapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class GatewayTokenFilter extends OncePerRequestFilter {

    private final String gatewayToken;

    public GatewayTokenFilter(@Value("${cart.security.gateway-token:}") String gatewayToken) {
        this.gatewayToken = gatewayToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/ws/cart") && !request.getRequestURI().startsWith("/ws/cart/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = request.getHeader("X-Gateway-Token");
        if (gatewayToken == null || gatewayToken.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Autenticação do Gateway não configurada.");
            return;
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                gatewayToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Requisição não encaminhada por um Gateway confiável.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
