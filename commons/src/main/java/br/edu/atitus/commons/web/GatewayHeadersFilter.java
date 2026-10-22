package br.edu.atitus.commons.web;

import br.edu.atitus.commons.context.UserContext;
import br.edu.atitus.commons.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class GatewayHeadersFilter extends OncePerRequestFilter {
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_TYPE_HEADER = "X-User-Type";
    public static final String USER_EMAIL_HEADER = "X-User-Email";

    private final byte[] expectedKey;

    public GatewayHeadersFilter(@Value("${internal.api-key}") String key) {
        this.expectedKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UserContextHolder.clear();
        String givenKey = request.getHeader(INTERNAL_KEY_HEADER);
        if (givenKey == null || !MessageDigest.isEqual(expectedKey,
                givenKey.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null) {
                try {
                    UserContextHolder.set(new UserContext(Long.valueOf(userId),
                            request.getHeader(USER_TYPE_HEADER), request.getHeader(USER_EMAIL_HEADER)));
                } catch (NumberFormatException exception) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-User-Id inválido");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}