package br.edu.atitus.inventoryapi.services;

import br.edu.atitus.inventoryapi.dtos.CreateReservationRequest;
import br.edu.atitus.inventoryapi.dtos.ReservationItemRequest;
import br.edu.atitus.inventoryapi.dtos.ReservationItemResponse;
import br.edu.atitus.inventoryapi.dtos.ReservationResponse;
import br.edu.atitus.inventoryapi.entities.InventoryEntity;
import br.edu.atitus.inventoryapi.entities.InventoryMovementEntity;
import br.edu.atitus.inventoryapi.entities.InventoryReservationEntity;
import br.edu.atitus.inventoryapi.entities.InventoryReservationItemEntity;
import br.edu.atitus.inventoryapi.enums.MovementType;
import br.edu.atitus.inventoryapi.enums.ProductType;
import br.edu.atitus.inventoryapi.enums.ReservationStatus;
import br.edu.atitus.inventoryapi.repositories.InventoryMovementRepository;
import br.edu.atitus.inventoryapi.repositories.InventoryRepository;
import br.edu.atitus.inventoryapi.repositories.InventoryReservationItemRepository;
import br.edu.atitus.inventoryapi.repositories.InventoryReservationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReservationService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);
    private static final int MAX_RESERVATION_ITEMS = 100;

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationItemRepository reservationItemRepository;
    private final InventoryMovementRepository movementRepository;
    private final EntityManager entityManager;

    public ReservationService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryReservationItemRepository reservationItemRepository,
            InventoryMovementRepository movementRepository,
            EntityManager entityManager
    ) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.reservationItemRepository = reservationItemRepository;
        this.movementRepository = movementRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        Map<Long, Long> requestedItems = validateAndNormalize(request);

        lockOrderId(request.orderId());
        var existing = reservationRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            InventoryReservationEntity reservation = existing.get();
            expireIfDue(reservation, Instant.now());
            List<InventoryReservationItemEntity> items = getItems(reservation.getId());
            if (!toQuantities(items).equals(requestedItems)) {
                throw conflict("O orderId já possui uma reserva com itens diferentes.");
            }
            return toResponse(reservation, items);
        }

        Map<Long, InventoryEntity> lockedInventory = lockInventoryRows(requestedItems);
        for (Map.Entry<Long, Long> requested : requestedItems.entrySet()) {
            InventoryEntity inventory = lockedInventory.get(requested.getKey());
            long quantity = requested.getValue();
            if (inventory.getProductType() == ProductType.SINGLE_UNIT && quantity > 1) {
                throw conflict("A reserva de um produto SINGLE_UNIT não pode exceder uma unidade.");
            }
            if (quantity > inventory.getAvailableQuantity()) {
                throw conflict("Quantidade indisponível para o produto " + inventory.getProductId() + ".");
            }
        }

        Instant now = Instant.now();
        InventoryReservationEntity reservation = reservationRepository.save(
                new InventoryReservationEntity(request.orderId(), now.plus(RESERVATION_TTL))
        );
        List<InventoryReservationItemEntity> items = new ArrayList<>();
        for (Map.Entry<Long, Long> requested : requestedItems.entrySet()) {
            InventoryEntity inventory = lockedInventory.get(requested.getKey());
            inventory.reserveQuantity(requested.getValue());
            items.add(new InventoryReservationItemEntity(reservation, inventory, requested.getValue()));
        }
        reservationItemRepository.saveAll(items);
        return toResponse(reservation, items);
    }

    @Transactional
    public ReservationResponse get(Long reservationId) {
        InventoryReservationEntity reservation = lockReservation(reservationId);
        expireIfDue(reservation, Instant.now());
        return toResponse(reservation, getItems(reservationId));
    }

    @Transactional
    public ReservationResponse confirm(Long reservationId) {
        InventoryReservationEntity reservation = lockReservation(reservationId);
        Instant now = Instant.now();
        expireIfDue(reservation, now);

        if (reservation.getStatus() == ReservationStatus.CONFIRMED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            return toResponse(reservation, getItems(reservationId));
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw conflict("Somente uma reserva ativa pode ser confirmada.");
        }

        List<InventoryReservationItemEntity> items = getItems(reservationId);
        Map<Long, InventoryEntity> lockedInventory = lockInventoryRows(toQuantities(items));
        if (!Instant.now().isBefore(reservation.getExpiresAt())) {
            expireIfDue(reservation, Instant.now());
            return toResponse(reservation, items);
        }
        for (InventoryReservationItemEntity item : items) {
            InventoryEntity inventory = lockedInventory.get(item.getProductId());
            inventory.confirmReservedQuantity(item.getQuantity());
            movementRepository.save(new InventoryMovementEntity(
                    inventory,
                    MovementType.RESERVATION_CONFIRMATION,
                    -item.getQuantity(),
                    "Confirmação da reserva " + reservationId,
                    reservation.getOrderId(),
                    reservationId
            ));
        }
        reservation.confirm(now);
        return toResponse(reservation, items);
    }

    @Transactional
    public ReservationResponse release(Long reservationId) {
        InventoryReservationEntity reservation = lockReservation(reservationId);
        Instant now = Instant.now();
        expireIfDue(reservation, now);

        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            return toResponse(reservation, getItems(reservationId));
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw conflict("Uma reserva confirmada não pode ser liberada.");
        }

        List<InventoryReservationItemEntity> items = getItems(reservationId);
        Map<Long, InventoryEntity> lockedInventory = lockInventoryRows(toQuantities(items));
        if (!Instant.now().isBefore(reservation.getExpiresAt())) {
            expireIfDue(reservation, Instant.now());
            return toResponse(reservation, items);
        }
        for (InventoryReservationItemEntity item : items) {
            lockedInventory.get(item.getProductId()).releaseReservedQuantity(item.getQuantity());
        }
        reservation.release(now);
        return toResponse(reservation, items);
    }

    @Transactional
    public int expireDueReservations() {
        Instant now = Instant.now();
        List<Long> dueReservationIds =
                reservationRepository.findExpiredReservationIds(
                        ReservationStatus.ACTIVE,
                        now,
                        PageRequest.of(0, 100)
                );
        int expiredCount = 0;
        for (Long reservationId : dueReservationIds) {
            InventoryReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Reserva não encontrada: " + reservationId
                    ));
            if (expireIfDue(reservation, now)) {
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private boolean expireIfDue(InventoryReservationEntity reservation, Instant now) {
        if (reservation.getStatus() != ReservationStatus.ACTIVE || now.isBefore(reservation.getExpiresAt())) {
            return false;
        }

        List<InventoryReservationItemEntity> items = getItems(reservation.getId());
        Map<Long, InventoryEntity> lockedInventory = lockInventoryRows(toQuantities(items));
        Instant expiredAt = Instant.now();
        if (expiredAt.isBefore(reservation.getExpiresAt())) {
            return false;
        }
        for (InventoryReservationItemEntity item : items) {
            lockedInventory.get(item.getProductId()).releaseReservedQuantity(item.getQuantity());
        }
        reservation.expire(expiredAt);
        return true;
    }

    private Map<Long, Long> validateAndNormalize(CreateReservationRequest request) {
        if (request == null || request.orderId() == null || request.orderId() <= 0) {
            throw badRequest("orderId deve ser positivo.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw badRequest("A reserva deve conter ao menos um item.");
        }
        if (request.items().size() > MAX_RESERVATION_ITEMS) {
            throw badRequest("A reserva aceita no máximo " + MAX_RESERVATION_ITEMS + " produtos.");
        }

        TreeMap<Long, Long> items = new TreeMap<>();
        for (ReservationItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0) {
                throw badRequest("Cada productId deve ser positivo.");
            }
            if (item.quantity() <= 0) {
                throw badRequest("A quantidade de cada item deve ser maior que zero.");
            }
            if (items.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw badRequest("A reserva não pode conter productIds duplicados.");
            }
        }
        return items;
    }

    private Map<Long, InventoryEntity> lockInventoryRows(Map<Long, Long> quantities) {
        Map<Long, InventoryEntity> locked = new LinkedHashMap<>();
        for (Long productId : quantities.keySet().stream().sorted().toList()) {
            InventoryEntity inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Estoque não encontrado para o produto " + productId + "."
                    ));
            locked.put(productId, inventory);
        }
        return locked;
    }

    private Map<Long, Long> toQuantities(List<InventoryReservationItemEntity> items) {
        Map<Long, Long> quantities = new TreeMap<>();
        for (InventoryReservationItemEntity item : items) {
            quantities.put(item.getProductId(), item.getQuantity());
        }
        return quantities;
    }

    private void lockOrderId(Long orderId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:orderId)")
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private InventoryReservationEntity lockReservation(Long reservationId) {
        if (reservationId == null || reservationId <= 0) {
            throw badRequest("reservationId deve ser positivo.");
        }
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Reserva não encontrada: " + reservationId
                ));
    }

    private List<InventoryReservationItemEntity> getItems(Long reservationId) {
        return reservationItemRepository.findAllByReservationIdOrderByProductId(reservationId);
    }

    private ReservationResponse toResponse(
            InventoryReservationEntity reservation,
            List<InventoryReservationItemEntity> items
    ) {
        return ReservationResponse.from(
                reservation,
                items.stream().map(ReservationItemResponse::from).toList()
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
