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
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final String serviceToken;

    public ServiceTokenFilter(@Value("${order.security.service-token:}") String serviceToken) {
        this.serviceToken = serviceToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.matches("/ws/orders/[0-9]+/(confirm|cancel)")
                && !path.equals("/ws/orders/internal/analytics")
                && !path.matches("/ws/orders/internal/[0-9]+/payment-info");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = request.getHeader("X-Service-Token");
        if (serviceToken == null || serviceToken.isBlank()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Autenticação serviço-a-serviço temporariamente indisponível.");
            return;
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Credencial de serviço inválida.");
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
}
