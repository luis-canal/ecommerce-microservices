package br.edu.atitus.analyticsapi.controllers;

import br.edu.atitus.analyticsapi.config.GatewayTokenFilter;
import br.edu.atitus.analyticsapi.services.AnalyticsAuthorizationService;
import br.edu.atitus.analyticsapi.services.AnalyticsQueryService;
import br.edu.atitus.analyticsapi.services.CollectionService;
import br.edu.atitus.analyticsapi.services.InventoryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminAnalyticsControllerTest {

    private final AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
    private final InventoryQueryService inventoryQueryService = mock(InventoryQueryService.class);
    private final CollectionService collectionService = mock(CollectionService.class);
    private final AdminAnalyticsController controller = new AdminAnalyticsController(
            queryService, inventoryQueryService, collectionService, new AnalyticsAuthorizationService());

    @Test
    void rejectsNonAdminBeforeQuerying() {
        ResponseStatusException customer = assertThrows(ResponseStatusException.class,
                () -> controller.overview(null, null, null, null, null, null, 1));
        ResponseStatusException anonymous = assertThrows(ResponseStatusException.class,
                () -> controller.inventory(null, null, "DAY", 10, null));

        assertEquals(HttpStatus.FORBIDDEN, customer.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, anonymous.getStatusCode());
        verifyNoInteractions(queryService, inventoryQueryService, collectionService);
    }

    @Test
    void validatesFilters() {
        ResponseStatusException invertedPeriod = assertThrows(ResponseStatusException.class,
                () -> controller.overview(java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 1, 1),
                        null, null, null, null, 0));
        ResponseStatusException invalidState = assertThrows(ResponseStatusException.class,
                () -> controller.salesByRegion(null, null, "ZZ", null, null, null, 0));
        ResponseStatusException invalidGranularity = assertThrows(ResponseStatusException.class,
                () -> controller.sales(null, null, null, null, null, null, "YEAR", 0));

        assertEquals(HttpStatus.BAD_REQUEST, invertedPeriod.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidState.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidGranularity.getStatusCode());
    }

    @Test
    void rejectsUnknownRegionAsFilter() {
        for (String region : new String[]{"UNKNOWN", "unknown", "LESTE"}) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> controller.overview(null, null, null, region, null, null, 0));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
        verifyNoInteractions(queryService);
    }

    @Test
    void gatewayFilterRejectsRequestsWithoutTrustedGatewayToken() throws Exception {
        GatewayTokenFilter filter = new GatewayTokenFilter("gateway-secret");

        MockHttpServletResponse direct = new MockHttpServletResponse();
        MockFilterChain directChain = new MockFilterChain();
        filter.doFilter(request(null), direct, directChain);

        MockHttpServletResponse viaGateway = new MockHttpServletResponse();
        MockFilterChain gatewayChain = new MockFilterChain();
        filter.doFilter(request("gateway-secret"), viaGateway, gatewayChain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), direct.getStatus());
        assertNull(directChain.getRequest());
        assertNotNull(gatewayChain.getRequest());
    }

    private static MockHttpServletRequest request(String gatewayToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/admin/analytics/overview");
        request.addHeader("X-User-Type", "0");
        if (gatewayToken != null) {
            request.addHeader("X-Gateway-Token", gatewayToken);
        }
        return request;
    }
}
