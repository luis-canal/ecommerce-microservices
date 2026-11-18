package br.edu.atitus.paymentapi.enums;

import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida do pagamento, independente dos estados do pedido.
 * As transições permitidas ficam somente aqui. Depois de encerrado, um pagamento só muda
 * em um caso: FAILED, EXPIRED ou CANCELLED que recebe aprovação do provedor (dinheiro
 * capturado que não será honrado) é estornado e passa a REFUNDED.
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    APPROVED,
    FAILED,
    EXPIRED,
    CANCELLED,
    /** Pago depois que o pedido deixou de aceitar pagamento (ex.: reserva expirada) e estornado. */
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            CREATED, Set.of(PENDING, FAILED, CANCELLED),
            PENDING, Set.of(APPROVED, FAILED, EXPIRED, CANCELLED, REFUNDED),
            // Somente para estornar aprovação recebida depois do encerramento.
            FAILED, Set.of(REFUNDED),
            EXPIRED, Set.of(REFUNDED),
            CANCELLED, Set.of(REFUNDED)
    );

    public static final Set<PaymentStatus> OPEN = Set.of(CREATED, PENDING);

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** Encerrado: não está mais aguardando o cliente nem o provedor. */
    public boolean isFinal() {
        return !OPEN.contains(this);
    }
}
