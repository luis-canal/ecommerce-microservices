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
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final String serviceToken;

    public ServiceTokenFilter(@Value("${inventory.security.service-token:}") String serviceToken) {
        this.serviceToken = serviceToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/ws/inventory/reservations")
                || path.startsWith("/ws/inventory/availability")
                || path.startsWith("/ws/inventory/analytics")
                || path.matches("/ws/inventory/[0-9]+/availability"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAdminReservationRead(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (serviceToken == null || serviceToken.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Autenticação serviço-a-serviço não configurada.");
            return;
        }

        String suppliedToken = request.getHeader("X-Service-Token");
        if (suppliedToken == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Credencial de serviço inválida.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminReservationRead(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && request.getRequestURI().matches("/ws/inventory/reservations/[0-9]+")
                && "0".equals(request.getHeader("X-User-Type"));
    }
}
