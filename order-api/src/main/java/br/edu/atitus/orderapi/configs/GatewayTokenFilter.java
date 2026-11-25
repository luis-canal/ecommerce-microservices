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
                || isAnalyticsPath(request.getRequestURI())
                || isPaymentInfoPath(request.getRequestURI()))
                && isValidServiceToken(suppliedServiceToken)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (gatewayToken == null || gatewayToken.isBlank()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Autenticação do Gateway temporariamente indisponível.");
            return;
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                gatewayToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Credencial de Gateway inválida.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(message);
    }

    private boolean isInternalTransitionPath(String path) {
        return path.matches("/ws/orders/[0-9]+/(confirm|cancel)");
    }

    private boolean isAnalyticsPath(String path) {
        return path.equals("/ws/orders/internal/analytics");
    }

    private boolean isPaymentInfoPath(String path) {
        return path.matches("/ws/orders/internal/[0-9]+/payment-info");
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
