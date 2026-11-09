package br.edu.atitus.orderapi.configs;

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
            @Value("${order.security.gateway-token:}") String gatewayToken,
            @Value("${order.security.service-token:}") String serviceToken
    ) {
        this.gatewayToken = gatewayToken;
        this.serviceToken = serviceToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/ws/orders")
                && !request.getRequestURI().startsWith("/ws/orders/")
                && !request.getRequestURI().equals("/ws/admin/orders")
                && !request.getRequestURI().startsWith("/ws/admin/orders/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = request.getHeader("X-Gateway-Token");
        String suppliedServiceToken = request.getHeader("X-Service-Token");
        if ((isInternalTransitionPath(request.getRequestURI())
                || isAnalyticsPath(request.getRequestURI()))
                && isValidServiceToken(suppliedServiceToken)) {
            filterChain.doFilter(request, response);
            return;
        }
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

    private boolean isInternalTransitionPath(String path) {
        return path.matches("/ws/orders/[0-9]+/(confirm|cancel)");
    }

    private boolean isAnalyticsPath(String path) {
        return path.equals("/ws/orders/internal/analytics");
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
