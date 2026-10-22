package br.edu.atitus.commons.web;

import br.edu.atitus.commons.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayHeadersFilterTest {
    private final GatewayHeadersFilter filter = new GatewayHeadersFilter("secret-key");

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void rejectsMissingOrIncorrectInternalKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/orders");
        request.addHeader(GatewayHeadersFilter.INTERNAL_KEY_HEADER, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertEquals(403, response.getStatus());
        assertFalse(chainCalled.get());
        assertNull(UserContextHolder.get());
    }

    @Test
    void acceptsTrustedKeySetsContextDuringRequestAndClearsItAfterward() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/orders");
        request.addHeader(GatewayHeadersFilter.INTERNAL_KEY_HEADER, "secret-key");
        request.addHeader(GatewayHeadersFilter.USER_ID_HEADER, "42");
        request.addHeader(GatewayHeadersFilter.USER_TYPE_HEADER, "ADMINISTRADOR");
        request.addHeader(GatewayHeadersFilter.USER_EMAIL_HEADER, "admin@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean contextWasAvailable = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> {
            contextWasAvailable.set(UserContextHolder.require().isAdmin()
                    && UserContextHolder.get().userId().equals(42L)
                    && UserContextHolder.get().email().equals("admin@example.test"));
        });

        assertTrue(contextWasAvailable.get());
        assertNull(UserContextHolder.get());
    }

    @Test
    void clearsContextWhenDownstreamChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/orders");
        request.addHeader(GatewayHeadersFilter.INTERNAL_KEY_HEADER, "secret-key");
        request.addHeader(GatewayHeadersFilter.USER_ID_HEADER, "42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new IOException("downstream failure");
        };

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> filter.doFilter(request, response, failingChain));
        assertNull(UserContextHolder.get());
    }
}