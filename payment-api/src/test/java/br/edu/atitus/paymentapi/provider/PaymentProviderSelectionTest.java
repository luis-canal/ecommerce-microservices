package br.edu.atitus.paymentapi.provider;

import br.edu.atitus.paymentapi.provider.simulated.SimulatedPaymentGateway;
import br.edu.atitus.paymentapi.provider.stripe.StripePaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Escolha do provedor por {@code payment.provider}, sem banco nem Docker. */
class PaymentProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StripePaymentGateway.class, SimulatedPaymentGateway.class);

    @Test
    void simulatedStartsWithoutStripeKeysOrReturnUrls() {
        runner.withPropertyValues("payment.provider=simulated").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PaymentGateway.class);
            assertThat(context).hasSingleBean(SimulatedPaymentGateway.class);
            assertThat(context).doesNotHaveBean(StripePaymentGateway.class);
        });
    }

    @Test
    void simulatedIsTheDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(SimulatedPaymentGateway.class));
    }

    @Test
    void stripeWithoutKeyStillFailsToStart() {
        runner.withPropertyValues("payment.provider=stripe").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sk_test_");
        });
    }

    @Test
    void stripeWithTestKeyCreatesOnlyTheStripeGateway() {
        runner.withPropertyValues(
                "payment.provider=stripe",
                "payment.stripe.secret-key=sk_test_selection",
                "payment.stripe.webhook-secret=whsec_selection",
                "payment.stripe.success-url=http://localhost/ok?session_id={CHECKOUT_SESSION_ID}",
                "payment.stripe.cancel-url=http://localhost/cancel"
        ).run(context -> {
            assertThat(context).hasSingleBean(StripePaymentGateway.class);
            assertThat(context).doesNotHaveBean(SimulatedPaymentGateway.class);
        });
    }
}
