package br.edu.atitus.orderapi.servicesimpl;

import br.edu.atitus.orderapi.clients.InventoryClient;
import br.edu.atitus.orderapi.clients.CurrencyClient;
import br.edu.atitus.orderapi.clients.ProductClient;
import br.edu.atitus.orderapi.dtos.CurrencyResponse;
import br.edu.atitus.orderapi.dtos.InventoryReservationRequest;
import br.edu.atitus.orderapi.dtos.InventoryReservationResponse;
import br.edu.atitus.orderapi.dtos.OrderDTO;
import br.edu.atitus.orderapi.dtos.OrderItemDTO;
import br.edu.atitus.orderapi.dtos.ProductResponse;
import br.edu.atitus.orderapi.dtos.ShippingAddressDTO;
import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderItemEntity;
import br.edu.atitus.orderapi.entities.OrderStatus;
import br.edu.atitus.orderapi.entities.ShippingAddress;
import br.edu.atitus.orderapi.repositories.OrderRepository;
import br.edu.atitus.orderapi.services.OrderService;
import br.edu.atitus.orderapi.services.ShippingCostCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderServiceJpa implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceJpa.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final CurrencyClient currencyClient;
    private final InventoryClient inventoryClient;
    private final OrderPersistenceService persistenceService;
    private final ShippingCostCalculator shippingCostCalculator;
    private final String inventoryServiceToken;

    public OrderServiceJpa(
            OrderRepository orderRepository,
            ProductClient productClient,
            CurrencyClient currencyClient,
            InventoryClient inventoryClient,
            OrderPersistenceService persistenceService,
            ShippingCostCalculator shippingCostCalculator,
            @Value("${order.clients.inventory-api.service-token:}") String inventoryServiceToken
    ) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.currencyClient = currencyClient;
        this.inventoryClient = inventoryClient;
        this.persistenceService = persistenceService;
        this.shippingCostCalculator = shippingCostCalculator;
        this.inventoryServiceToken = inventoryServiceToken;
    }

    @Override
    public OrderEntity createOrder(OrderDTO request, Long userId, String idempotencyKey) {
        Map<Long, Integer> requestedItems = validateRequest(request, userId);
        ShippingAddress shippingAddress = toShippingAddress(request.shippingAddress());
        BigDecimal shippingAmount = shippingCostCalculator.calculate(shippingAddress.getState());
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprint(requestedItems, shippingAddress);

        if (normalizedKey != null) {
            var existing = orderRepository.findByCustomerIdAndIdempotencyKey(userId, normalizedKey);
            if (existing.isPresent()) {
                return resumeOrReturn(existing.get(), fingerprint);
            }
        }

        OrderEntity order = buildOrder(requestedItems);
        order.setCustomerId(userId);
        order.setShippingAddress(shippingAddress);
        order.setShippingAmount(shippingAmount);
        order.setTotalAmount(order.getSubtotal().subtract(order.getDiscountAmount()).add(shippingAmount));
        if (order.getTotalAmount().precision() > 19) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total do pedido excede o limite financeiro permitido."
            );
        }
        order.setIdempotencyKey(normalizedKey);
        order.setRequestFingerprint(fingerprint);

        try {
            order = persistenceService.savePending(order);
        } catch (DataIntegrityViolationException exception) {
            if (normalizedKey == null) {
                throw exception;
            }
            return orderRepository.findByCustomerIdAndIdempotencyKey(userId, normalizedKey)
                    .map(existing -> resumeOrReturn(existing, fingerprint))
                    .orElseThrow(() -> exception);
        }
        return reserveAndFinalize(order);
    }

    @Override
    public Page<OrderEntity> findOrdersByCustomerId(Long customerId, String targetCurrency, Pageable pageable) {
        String normalizedCurrency = normalizeCurrency(targetCurrency);
        Page<Long> orderIds = orderRepository.findIdsByCustomerId(customerId, pageable);
        if (orderIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, orderIds.getTotalElements());
        }
        Map<Long, OrderEntity> ordersById = orderRepository.findAllWithItemsByIdIn(orderIds.getContent()).stream()
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        List<OrderEntity> orderedResults = orderIds.getContent().stream()
                .map(ordersById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        Page<OrderEntity> orders = new PageImpl<>(orderedResults, pageable, orderIds.getTotalElements());
        BigDecimal conversionRate = BigDecimal.ONE;
        if (!"BRL".equals(normalizedCurrency) && !orders.isEmpty()) {
            CurrencyResponse currencyResponse;
            try {
                currencyResponse = currencyClient.getCurrency("BRL", normalizedCurrency);
            } catch (RestClientResponseException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao consultar o serviço de câmbio.");
            } catch (ResourceAccessException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de câmbio indisponível.");
            }
            if (currencyResponse == null || currencyResponse.conversionRate() == null
                    || !Double.isFinite(currencyResponse.conversionRate())
                    || currencyResponse.conversionRate() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Taxa de conversão inválida.");
            }
            conversionRate = BigDecimal.valueOf(currencyResponse.conversionRate());
        }

        for (OrderEntity order : orders.getContent()) {
            prepareOrderFromSnapshot(order, normalizedCurrency, conversionRate);
        }
        return orders;
    }

    @Override
    public OrderEntity findOrderByCustomerIdAndId(Long customerId, Long orderId) {
        OrderEntity order = orderRepository.findWithItemsByCustomerIdAndId(customerId, orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        prepareOrderFromSnapshot(order, "BRL", BigDecimal.ONE);
        return order;
    }

    @Override
    public Page<OrderEntity> findOrdersForAdmin(
            Long orderId,
            OrderStatus status,
            Long customerId,
            Instant createdFrom,
            Instant createdTo,
            Instant confirmedFrom,
            Instant confirmedTo,
            Pageable pageable
    ) {
        validateRange(createdFrom, createdTo, "criação");
        validateRange(confirmedFrom, confirmedTo, "confirmação");
        Page<Long> ids = orderRepository.findIdsForAdmin(
                orderId, status, customerId, createdFrom, createdTo, confirmedFrom, confirmedTo, pageable
        );
        return loadOrdersWithSnapshots(ids, pageable);
    }

    @Override
    public OrderEntity findOrderForAdmin(Long orderId) {
        OrderEntity order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        prepareOrderFromSnapshot(order, "BRL", BigDecimal.ONE);
        return order;
    }

    @Override
    public Page<OrderEntity> findOrdersUpdatedAfter(Instant updatedAfter, Pageable pageable) {
        if (updatedAfter == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "updatedAfter é obrigatório.");
        }
        Page<Long> ids = orderRepository.findIdsUpdatedAfter(updatedAfter, pageable);
        return loadOrdersWithSnapshots(ids, pageable);
    }

    @Override
    public OrderEntity confirmOrder(Long orderId) {
        OrderEntity order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getReservationId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido não está aguardando pagamento.");
        }

        InventoryReservationResponse reservation;
        try {
            reservation = inventoryClient.confirm(order.getReservationId(), inventoryServiceToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Não foi possível confirmar a reserva; o pedido pode ser reconciliado."
                );
            }
            reservation = getReservationAfterConflict(
                    order.getReservationId(),
                    "Não foi possível consultar a reserva após a tentativa de confirmação."
            );
            if ("EXPIRED".equals(reservation.status())) {
                return persistenceService.updateStatus(orderId, OrderStatus.EXPIRED);
            }
            if ("CONFIRMED".equals(reservation.status())) {
                return persistenceService.updateStatus(orderId, OrderStatus.CONFIRMED);
            }
            if ("RELEASED".equals(reservation.status())) {
                persistenceService.updateStatus(orderId, OrderStatus.CANCELLED);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva de estoque não pode ser confirmada.");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível confirmar a reserva; o pedido pode ser reconciliado."
            );
        }
        if ("EXPIRED".equals(reservation.status())) {
            return persistenceService.updateStatus(orderId, OrderStatus.EXPIRED);
        }
        if (!"CONFIRMED".equals(reservation.status())) {
            if ("RELEASED".equals(reservation.status())) {
                persistenceService.updateStatus(orderId, OrderStatus.CANCELLED);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva de estoque não pode ser confirmada.");
        }
        return persistenceService.updateStatus(orderId, OrderStatus.CONFIRMED);
    }

    @Override
    public OrderEntity cancelOrder(Long orderId) {
        OrderEntity order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            return order;
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido confirmado não pode ser cancelado.");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getReservationId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido não pode ser cancelado neste estado.");
        }

        InventoryReservationResponse reservation;
        try {
            reservation = inventoryClient.release(order.getReservationId(), inventoryServiceToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Não foi possível liberar a reserva; o pedido pode ser reconciliado."
                );
            }
            reservation = getReservationAfterConflict(
                    order.getReservationId(),
                    "Não foi possível consultar a reserva após a tentativa de liberação."
            );
            if ("EXPIRED".equals(reservation.status())) {
                return persistenceService.updateStatus(orderId, OrderStatus.EXPIRED);
            }
            if ("RELEASED".equals(reservation.status())) {
                return persistenceService.updateStatus(orderId, OrderStatus.CANCELLED);
            }
            if ("CONFIRMED".equals(reservation.status())) {
                persistenceService.updateStatus(orderId, OrderStatus.CONFIRMED);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva de estoque não pode ser liberada.");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível liberar a reserva; o pedido pode ser reconciliado."
            );
        }
        if ("EXPIRED".equals(reservation.status())) {
            return persistenceService.updateStatus(orderId, OrderStatus.EXPIRED);
        }
        if (!"RELEASED".equals(reservation.status())) {
            if ("CONFIRMED".equals(reservation.status())) {
                persistenceService.updateStatus(orderId, OrderStatus.CONFIRMED);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva de estoque não pode ser liberada.");
        }
        return persistenceService.updateStatus(orderId, OrderStatus.CANCELLED);
    }

    @Override
    public int reconcileExpiredReservations() {
        Instant now = Instant.now();
        int updated = reconcilePendingReservations(now.minusSeconds(30));
        List<OrderEntity> dueOrders = orderRepository.findByStatusAndReservationExpiresAtBefore(
                OrderStatus.PENDING_PAYMENT,
                now
        );
        for (OrderEntity order : dueOrders) {
            try {
                InventoryReservationResponse reservation =
                        inventoryClient.get(order.getReservationId(), inventoryServiceToken);
                if ("EXPIRED".equals(reservation.status())) {
                    persistenceService.updateStatus(order.getId(), OrderStatus.EXPIRED);
                    updated++;
                } else if ("RELEASED".equals(reservation.status())) {
                    persistenceService.updateStatus(order.getId(), OrderStatus.CANCELLED);
                    updated++;
                } else if ("CONFIRMED".equals(reservation.status())) {
                    persistenceService.updateStatus(order.getId(), OrderStatus.CONFIRMED);
                    updated++;
                }
            } catch (RestClientResponseException | ResourceAccessException exception) {
                LOGGER.warn("Não foi possível reconciliar a reserva do pedido {} agora.", order.getId(), exception);
            }
        }
        return updated;
    }

    private int reconcilePendingReservations(Instant olderThan) {
        List<OrderEntity> pendingOrders = orderRepository.findByStatusAndOrderDateBefore(
                OrderStatus.RESERVATION_PENDING,
                olderThan
        );
        int updated = 0;
        for (OrderEntity order : pendingOrders) {
            try {
                OrderEntity resumed = reserveAndFinalize(order);
                if (resumed.getStatus() != OrderStatus.RESERVATION_PENDING) {
                    updated++;
                }
            } catch (ResponseStatusException | RestClientResponseException | ResourceAccessException exception) {
                LOGGER.warn(
                        "Não foi possível recuperar a criação da reserva do pedido {} agora.",
                        order.getId(),
                        exception
                );
            }
        }
        return updated;
    }

    private Map<Long, Integer> validateRequest(OrderDTO request, Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade de cliente inválida.");
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O pedido deve conter ao menos um item.");
        }
        if (request.items().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O pedido não pode conter mais de 100 itens.");
        }
        Map<Long, Integer> requestedItems = new TreeMap<>();
        for (OrderItemDTO item : request.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cada item deve ter productId e quantity positivos."
                );
            }
            if (requestedItems.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productIds duplicados não são permitidos.");
            }
        }
        return requestedItems;
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key deve ter até 128 caracteres.");
        }
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        String normalized = currency == null || currency.isBlank() ? "BRL" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCurrency deve ser um código ISO de 3 letras.");
        }
        return normalized;
    }

    private String fingerprint(Map<Long, Integer> requestedItems, ShippingAddress address) {
        StringBuilder canonical = new StringBuilder();
        requestedItems.forEach((productId, quantity) -> {
            appendFingerprintPart(canonical, productId.toString());
            appendFingerprintPart(canonical, quantity.toString());
        });
        appendFingerprintPart(canonical, address.getZipCode());
        appendFingerprintPart(canonical, address.getStreet());
        appendFingerprintPart(canonical, address.getNumber());
        appendFingerprintPart(canonical, address.getComplement());
        appendFingerprintPart(canonical, address.getNeighborhood());
        appendFingerprintPart(canonical, address.getCity());
        appendFingerprintPart(canonical, address.getState());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }

    private void appendFingerprintPart(StringBuilder canonical, String value) {
        String normalized = value == null ? "" : value;
        canonical.append(normalized.length()).append(':').append(normalized);
    }

    private OrderEntity buildOrder(Map<Long, Integer> requestedItems) {
        OrderEntity order = new OrderEntity();
        List<OrderItemEntity> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> requested : requestedItems.entrySet()) {
            ProductResponse product;
            try {
                product = productClient.getProductById(requested.getKey(), "BRL");
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "Produto indisponível no catálogo: " + requested.getKey()
                    );
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao consultar o catálogo.");
            } catch (ResourceAccessException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo indisponível.");
            }
            if (product == null || product.id() == null || !product.id().equals(requested.getKey())
                    || product.active() == null || product.productType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O catálogo retornou dados incompletos.");
            }
            if (product.convertedPrice() == null || product.convertedPrice().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O catálogo retornou preço inválido.");
            }
            if (product.price() == null || product.price().signum() <= 0
                    || product.price().precision() > 19
                    || product.currency() == null || !product.currency().matches("[A-Za-z]{3}")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O catálogo retornou preço/moeda inválidos.");
            }
            BigDecimal purchasePrice = product.convertedPrice().setScale(2, RoundingMode.HALF_UP);
            if (purchasePrice.signum() <= 0 || purchasePrice.precision() > 19) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O catálogo retornou preço fora do limite permitido.");
            }
            if (!product.active()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Produto indisponível no catálogo: " + requested.getKey()
                );
            }

            OrderItemEntity item = new OrderItemEntity();
            item.setProductId(requested.getKey());
            item.setQuantity(requested.getValue());
            item.setPriceAtPurchase(purchasePrice);
            item.setCurrencyAtPurchase("BRL");
            item.setCatalogPriceAtPurchase(product.price().setScale(2, RoundingMode.HALF_UP));
            item.setCatalogCurrencyAtPurchase(product.currency().toUpperCase(Locale.ROOT));
            item.setDiscountAmount(BigDecimal.ZERO.setScale(2));
            item.setSubtotal(purchasePrice.multiply(BigDecimal.valueOf(requested.getValue()))
                    .setScale(2, RoundingMode.HALF_UP));
            item.setConvertedPriceAtPruchase(item.getPriceAtPurchase());
            item.setProductName(nonBlank(product.name(), product.model(), "Produto " + product.id()));
            item.setProductBrand(product.brand());
            item.setProductModel(product.model());
            item.setProductDescription(product.description());
            item.setProductImage(product.image());
            item.setCategoryId(product.categoryId());
            item.setCategoryName(product.categoryName());
            item.setSubcategoryId(product.subcategoryId());
            item.setSubcategoryName(product.subcategoryName());
            item.setProductType(product.productType() == null ? null : product.productType().toString());
            item.setProductActive(product.active());
            item.setProductEnvironment(product.environment());
            item.setPromotionMessage(product.promotionMessage());
            if (item.getSubtotal().precision() > 19) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Subtotal do item excede o limite financeiro permitido."
                );
            }
            item.setProduct(product);
            item.setOrder(order);
            items.add(item);
        }
        order.setItems(items);
        BigDecimal total = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.precision() > 19) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Subtotal do pedido excede o limite financeiro permitido."
            );
        }
        order.setSubtotal(total);
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setShippingAmount(BigDecimal.ZERO.setScale(2));
        order.setTotalAmount(total);
        order.setCurrency("BRL");
        order.setTotalPrice(total);
        order.setTotalConvertedPrice(total);
        return order;
    }

    private ShippingAddress toShippingAddress(ShippingAddressDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço de entrega é obrigatório.");
        }
        String zipCode = requiredAddressValue(request.zipCode(), "CEP", 9);
        if (!zipCode.matches("\\d{5}-?\\d{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP inválido.");
        }
        String street = requiredAddressValue(request.street(), "Rua", 200);
        String number = requiredAddressValue(request.number(), "Número", 32);
        String neighborhood = requiredAddressValue(request.neighborhood(), "Bairro", 120);
        String city = requiredAddressValue(request.city(), "Cidade", 120);
        String state = requiredAddressValue(request.state(), "UF", 2).toUpperCase(Locale.ROOT);
        if (!state.matches("[A-Z]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UF inválida.");
        }
        String complement = optionalAddressValue(request.complement(), 200);

        ShippingAddress address = new ShippingAddress();
        address.setZipCode(zipCode);
        address.setStreet(street);
        address.setNumber(number);
        address.setComplement(complement);
        address.setNeighborhood(neighborhood);
        address.setCity(city);
        address.setState(state);
        return address;
    }

    private String requiredAddressValue(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " do endereço de entrega é obrigatório e deve respeitar o limite de tamanho."
            );
        }
        return normalized;
    }

    private String optionalAddressValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complemento do endereço excede o limite.");
        }
        return normalized;
    }

    private String nonBlank(String value, String fallback, String finalFallback) {
        return value == null || value.isBlank()
                ? (fallback == null || fallback.isBlank() ? finalFallback : fallback)
                : value;
    }

    private void prepareOrderFromSnapshot(OrderEntity order, String targetCurrency, BigDecimal conversionRate) {
        for (OrderItemEntity item : order.getItems()) {
            BigDecimal converted = item.getPriceAtPurchase()
                    .multiply(conversionRate)
                    .setScale(2, RoundingMode.HALF_UP);
            item.setConvertedPriceAtPruchase(converted);
            item.setProduct(ProductResponse.fromSnapshot(item, targetCurrency, converted));
        }
        order.setTotalPrice(order.getSubtotal());
        order.setTotalConvertedPrice(order.getTotalAmount()
                .multiply(conversionRate)
                .setScale(2, RoundingMode.HALF_UP));
    }

    private Page<OrderEntity> loadOrdersWithSnapshots(Page<Long> ids, Pageable pageable) {
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }
        Map<Long, OrderEntity> ordersById = orderRepository.findAllWithItemsByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        List<OrderEntity> orderedResults = ids.getContent().stream()
                .map(ordersById::get)
                .filter(java.util.Objects::nonNull)
                .peek(order -> prepareOrderFromSnapshot(order, "BRL", BigDecimal.ONE))
                .toList();
        return new PageImpl<>(orderedResults, pageable, ids.getTotalElements());
    }

    private void validateRange(Instant start, Instant end, String label) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Período de " + label + " inválido: o início deve anteceder o fim."
            );
        }
    }

    private InventoryReservationResponse getReservationAfterConflict(Long reservationId, String message) {
        try {
            return inventoryClient.get(reservationId, inventoryServiceToken);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, exception);
        }
    }

    private OrderEntity resumeOrReturn(OrderEntity existing, String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key já foi usado com conteúdo diferente."
            );
        }
        if (existing.getStatus() == OrderStatus.RESERVATION_PENDING) {
            return reserveAndFinalize(existing);
        }
        return existing;
    }

    private OrderEntity reserveAndFinalize(OrderEntity order) {
        InventoryReservationResponse reservation;
        try {
            reservation = inventoryClient.create(toReservationRequest(order), inventoryServiceToken);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == HttpStatus.CONFLICT.value()) {
                persistenceService.deleteRejectedOrder(order.getId());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque indisponível para o pedido.");
            }
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Resultado da reserva incerto; o pedido pode ser retomado pela mesma Idempotency-Key."
            );
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Resultado da reserva incerto; o pedido pode ser retomado pela mesma Idempotency-Key."
            );
        }

        if (reservation == null || reservation.reservationId() == null
                || reservation.expiresAt() == null || reservation.status() == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory API retornou uma reserva incompleta; o pedido pode ser recuperado."
            );
        }
        OrderStatus orderStatus = switch (reservation.status()) {
            case "ACTIVE" -> {
                if (!reservation.expiresAt().isAfter(Instant.now())) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Inventory API retornou uma reserva ativa expirada; o pedido pode ser recuperado."
                    );
                }
                yield OrderStatus.PENDING_PAYMENT;
            }
            case "EXPIRED" -> OrderStatus.EXPIRED;
            case "RELEASED" -> OrderStatus.CANCELLED;
            case "CONFIRMED" -> OrderStatus.CONFIRMED;
            default -> throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory API retornou estado de reserva desconhecido."
            );
        };
        OrderEntity updatedOrder = persistenceService.attachReservation(
                order.getId(),
                reservation.reservationId(),
                reservation.expiresAt(),
                orderStatus
        );
        if (orderStatus == OrderStatus.CONFIRMED || orderStatus == OrderStatus.CANCELLED) {
            return persistenceService.updateStatus(order.getId(), orderStatus);
        }
        return updatedOrder;
    }

    private InventoryReservationRequest toReservationRequest(OrderEntity order) {
        List<InventoryReservationRequest.Item> items = order.getItems().stream()
                .map(item -> new InventoryReservationRequest.Item(item.getProductId(), item.getQuantity()))
                .toList();
        return new InventoryReservationRequest(order.getId(), items);
    }
}
