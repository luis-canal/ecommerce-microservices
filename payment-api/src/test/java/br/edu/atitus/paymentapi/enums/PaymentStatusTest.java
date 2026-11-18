package br.edu.atitus.paymentapi.enums;

import br.edu.atitus.paymentapi.entities.PaymentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentStatusTest {

    @Test
    void openStatesFollowTheLifecycle() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.PENDING)).isTrue();
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.APPROVED)).isFalse();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.APPROVED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CREATED)).isFalse();
    }

    @Test
    void approvedAndRefundedNeverChange() {
        for (PaymentStatus status : EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.REFUNDED)) {
            assertThat(status.isFinal()).isTrue();
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(status.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    void closedWithoutPaymentCanOnlyBeRefunded() {
        for (PaymentStatus status : EnumSet.of(PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED)) {
            assertThat(status.isFinal()).isTrue();
            for (PaymentStatus target : PaymentStatus.values()) {
                assertThat(status.canTransitionTo(target)).isEqualTo(target == PaymentStatus.REFUNDED);
            }
        }
    }

    @Test
    void entityRejectsInvalidTransition() {
        PaymentEntity payment = new PaymentEntity(1L, 1L, BigDecimal.TEN, "BRL", Instant.now());

        assertThrows(IllegalStateException.class, () -> payment.transitionTo(PaymentStatus.APPROVED));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }
}
