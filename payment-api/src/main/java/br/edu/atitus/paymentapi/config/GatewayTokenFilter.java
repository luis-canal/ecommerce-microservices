package br.edu.atitus.paymentapi.config;

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

/**
 * Garante que as rotas /ws/payments/** só sejam acessadas via Gateway confiável.
 * O webhook (/payments/webhook) fica fora deste filtro: é autenticado pela assinatura do Stripe.
 * O Gateway remove cabeçalhos X-User-* enviados pelo cliente e injeta os valores do JWT,
 * por isso o X-User-Type só é confiável quando acompanhado do X-Gateway-Token.
 */
@Component
public class GatewayTokenFilter extends OncePerRequestFilter {

    private final String gatewayToken;

    public GatewayTokenFilter(@Value("${payment.security.gateway-token:}") String gatewayToken) {
        this.gatewayToken = gatewayToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/ws/payments") && !path.startsWith("/ws/payments/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (gatewayToken == null || gatewayToken.isBlank()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Autenticação do Gateway temporariamente indisponível.");
            return;
        }

        String suppliedToken = request.getHeader("X-Gateway-Token");
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
}
