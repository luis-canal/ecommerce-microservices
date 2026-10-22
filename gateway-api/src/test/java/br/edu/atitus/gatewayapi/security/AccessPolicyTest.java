package br.edu.atitus.gatewayapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessPolicyTest {
    private final AccessPolicy policy = new AccessPolicy(false);

    @Test
    void blocksInternalAndActuatorForAllMethods() {
        assertEquals(Access.BLOCKED, policy.resolve(HttpMethod.GET, "/internal/tokens/x/revoked"));
        assertEquals(Access.BLOCKED, policy.resolve(HttpMethod.POST, "/actuator/env"));
    }

    @Test
    void onlyAllowsExplicitPublicMethodAndPaths() {
        assertEquals(Access.PUBLIC, policy.resolve(HttpMethod.POST, "/auth/signup"));
        assertEquals(Access.PUBLIC, policy.resolve(HttpMethod.GET, "/products/1"));
        assertEquals(Access.AUTHENTICATED, policy.resolve(HttpMethod.GET, "/auth/signup"));
        assertEquals(Access.AUTHENTICATED, policy.resolve(HttpMethod.POST, "/products"));
    }

    @Test
    void protectsAdminAndDefaultsUnknownPathsToAuthenticated() {
        assertEquals(Access.ADMIN, policy.resolve(HttpMethod.DELETE, "/ws/admin/orders/1"));
        assertEquals(Access.AUTHENTICATED, policy.resolve(HttpMethod.GET, "/unlisted"));
    }

    @Test
    void exposesSwaggerOnlyWhenDevelopmentProfileIsEnabled() {
        assertEquals(Access.AUTHENTICATED, policy.resolve(HttpMethod.GET, "/docs"));
        assertEquals(Access.PUBLIC, new AccessPolicy(true).resolve(HttpMethod.GET, "/docs"));
    }
}