package br.edu.atitus.paymentapi.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentReturnControllerTest {

    private final PaymentReturnController controller =
            new PaymentReturnController("oma://payment/success", "oma://payment/cancel");

    @Test
    void successRedirectsToAppWithOrderAndSession() {
        var response = controller.success("cs_test_a1B2c3", "42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("oma://payment/success?order_id=42&session_id=cs_test_a1B2c3");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void successKeepsSimulatedSessionId() {
        var response = controller.success("sim_15", "42");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("oma://payment/success?order_id=42&session_id=sim_15");
    }

    @Test
    void cancelRedirectsToAppWithOrder() {
        var response = controller.cancel("42");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("oma://payment/cancel?order_id=42");
    }

    @Test
    void dropsParametersWithUnexpectedFormat() {
        var response = controller.success("cs_test_1&x=https://evil.example", "42abc");

        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("oma://payment/success");
    }

    @Test
    void keepsExistingQueryInTarget() {
        var custom = new PaymentReturnController("exp://192.168.0.10:8081/--/payment/success?a=1", "oma://payment/cancel");

        assertThat(custom.success(null, "5").getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("exp://192.168.0.10:8081/--/payment/success?a=1&order_id=5");
    }

    @Test
    void requiresAppUrls() {
        assertThrows(IllegalStateException.class, () -> new PaymentReturnController(" ", "oma://payment/cancel"));
        assertThrows(IllegalStateException.class, () -> new PaymentReturnController("oma://payment/success", null));
    }
}
