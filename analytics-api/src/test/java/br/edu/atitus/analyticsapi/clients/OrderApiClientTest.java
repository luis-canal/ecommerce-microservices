package br.edu.atitus.analyticsapi.clients;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrderApiClientTest {

    private static final String URL = "http://order-api/ws/orders/internal/analytics"
            + "?updatedAfter=2026-01-01T00%3A00%3A00Z&page=0&size=100&sort=updatedAt,asc&sort=id,asc";

    @Test
    void sendsServiceTokenRetriesServerErrorsAndParsesPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(URL))
                .andExpect(header("X-Service-Token", "secret"))
                .andRespond(withSuccess("""
                        {"content":[{"id":1,"customerId":2,"status":"CONFIRMED","currency":"BRL",
                          "updatedAt":"2026-01-02T10:00:00Z","totalAmount":99.90,
                          "shippingAddress":{"state":"SP","city":"São Paulo"},
                          "items":[{"id":5,"productId":9,"quantity":1,"priceAtPurchase":99.90,
                                    "productName":"X","categoryId":3,"product":null}]}],
                         "page":{"size":100,"number":0,"totalElements":1,"totalPages":1}}
                        """, MediaType.APPLICATION_JSON));

        OrderApiClient client = new OrderApiClient(builder, "http://order-api", "secret", new RetryExecutor(3, 0));
        PageResponse<SourceOrder> page = client.fetchOrdersUpdatedAfter(Instant.parse("2026-01-01T00:00:00Z"), 0, 100);

        server.verify();
        assertThat(page.hasNext(0, 100)).isFalse();
        assertThat(page.safeContent()).singleElement().satisfies(order -> {
            assertThat(order.status()).isEqualTo("CONFIRMED");
            assertThat(order.shippingAddress().state()).isEqualTo("SP");
            assertThat(order.items()).singleElement().extracting(SourceOrder.Item::productId).isEqualTo(9L);
        });
    }

    @Test
    void doesNotRetryClientErrorsAndFailsWithoutServiceToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        OrderApiClient client = new OrderApiClient(builder, "http://order-api", "secret", new RetryExecutor(3, 0));
        assertThatThrownBy(() -> client.fetchOrdersUpdatedAfter(Instant.parse("2026-01-01T00:00:00Z"), 0, 100))
                .isInstanceOf(IllegalStateException.class);
        server.verify();

        OrderApiClient unconfigured = new OrderApiClient(RestClient.builder(), "http://order-api", "",
                new RetryExecutor(1, 0));
        assertThatThrownBy(() -> unconfigured.fetchOrdersUpdatedAfter(Instant.EPOCH, 0, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_AUTH_TOKEN");
    }
}
