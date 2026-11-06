package br.edu.atitus.inventoryapi.config;

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
    private final String serviceToken;

    public GatewayTokenFilter(
            @Value("${inventory.security.gateway-token:}") String gatewayToken,
            @Value("${inventory.security.service-token:}") String serviceToken
    ) {
        this.gatewayToken = gatewayToken;
        this.serviceToken = serviceToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/ws/inventory") && !path.startsWith("/ws/inventory/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isInternalServicePath(request.getRequestURI()) && isValidServiceToken(request.getHeader("X-Service-Token"))) {
            filterChain.doFilter(request, response);
            return;
        }

        if (gatewayToken == null || gatewayToken.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Autenticação do Gateway não configurada.");
            return;
        }

        String suppliedToken = request.getHeader("X-Gateway-Token");
        if (suppliedToken == null || !MessageDigest.isEqual(
                gatewayToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Requisição não encaminhada por um Gateway confiável.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInternalServicePath(String path) {
        return path.startsWith("/ws/inventory/reservations")
                || path.startsWith("/ws/inventory/availability")
                || path.startsWith("/ws/inventory/analytics")
                || path.matches("/ws/inventory/[0-9]+/availability");
    }

    private boolean isValidServiceToken(String suppliedToken) {
        return serviceToken != null
                && !serviceToken.isBlank()
                && suppliedToken != null
                && MessageDigest.isEqual(
                        serviceToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8)
                );
    }
}
