package br.edu.atitus.orderapi.entities;

public enum OrderStatus {
    RESERVATION_PENDING,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    FAILED,
    LEGACY
}
