package br.edu.atitus.paymentapi.clients;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestOrderApiClientTest {

    private MockRestServiceServer server;
    private RestOrderApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestOrderApiClient(builder, "http://order-api", "service-token");
    }

    @Test
    void readsPaymentInfoWithServiceToken() {
        server.expect(requestTo("http://order-api/ws/orders/internal/7/payment-info"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", "service-token"))
                .andRespond(withSuccess("""
                        {"id":7,"customerId":3,"status":"PENDING_PAYMENT","totalAmount":149.90,
                         "currency":"BRL","reservationExpiresAt":"2026-11-18T12:10:00Z"}
                        """, MediaType.APPLICATION_JSON));

        OrderPaymentInfo info = client.getPaymentInfo(7L).orElseThrow();

        assertThat(info.customerId()).isEqualTo(3L);
        assertThat(info.totalAmount()).isEqualByComparingTo("149.90");
        server.verify();
    }

    @Test
    void missingOrderIsEmpty() {
        server.expect(requestTo("http://order-api/ws/orders/internal/7/payment-info"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getPaymentInfo(7L)).isEmpty();
    }

    @Test
    void confirmReturnsStatusFromBody() {
        server.expect(requestTo("http://order-api/ws/orders/7/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "service-token"))
                .andRespond(withSuccess("{\"id\":7,\"status\":\"EXPIRED\",\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.confirm(7L)).isEqualTo("EXPIRED");
    }

    @Test
    void confirmConflictIsNull() {
        server.expect(requestTo("http://order-api/ws/orders/7/confirm")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThat(client.confirm(7L)).isNull();
    }

    @Test
    void confirmNotFoundIsAnError() {
        server.expect(requestTo("http://order-api/ws/orders/7/confirm")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(OrderApiException.class, () -> client.confirm(7L));
    }

    @Test
    void confirmServerErrorIsAnError() {
        server.expect(requestTo("http://order-api/ws/orders/7/confirm"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(OrderApiException.class, () -> client.confirm(7L));
    }

    @Test
    void cancelConflictIsOnlyLogged() {
        server.expect(requestTo("http://order-api/ws/orders/7/cancel")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertDoesNotThrow(() -> client.cancel(7L));
    }

    @Test
    void missingServiceTokenFailsBeforeCalling() {
        RestOrderApiClient unconfigured = new RestOrderApiClient(RestClient.builder(), "http://order-api", "");

        assertThrows(OrderApiException.class, () -> unconfigured.confirm(7L));
    }
}
