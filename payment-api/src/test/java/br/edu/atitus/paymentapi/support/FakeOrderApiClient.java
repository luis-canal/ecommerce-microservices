package br.edu.atitus.paymentapi.support;

import br.edu.atitus.paymentapi.clients.OrderApiClient;
import br.edu.atitus.paymentapi.clients.OrderApiException;
import br.edu.atitus.paymentapi.clients.OrderPaymentInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** order-api em memória. */
public class FakeOrderApiClient implements OrderApiClient {

    public final Map<Long, OrderPaymentInfo> orders = new ConcurrentHashMap<>();
    public final List<Long> confirmCalls = new CopyOnWriteArrayList<>();
    public final List<Long> cancelCalls = new CopyOnWriteArrayList<>();

    /** Status devolvido pelo confirm; {@code null} simula 409. */
    public volatile String confirmStatus = "CONFIRMED";
    /** Quando preenchido, confirm e cancel lançam este erro (order-api fora do ar, 404, timeout). */
    public volatile OrderApiException failure;

    public static OrderPaymentInfo pendingOrder(Long orderId, Long customerId, String total) {
        return new OrderPaymentInfo(orderId, customerId, "PENDING_PAYMENT", new BigDecimal(total), "BRL",
                Instant.now().plusSeconds(600));
    }

    @Override
    public Optional<OrderPaymentInfo> getPaymentInfo(Long orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public String confirm(Long orderId) {
        if (failure != null) {
            throw failure;
        }
        confirmCalls.add(orderId);
        return confirmStatus;
    }

    @Override
    public void cancel(Long orderId) {
        if (failure != null) {
            throw failure;
        }
        cancelCalls.add(orderId);
    }
}
