package br.edu.atitus.inventoryapi.dtos;

import java.util.List;

public record AvailabilityBatchRequest(List<Long> productIds) {
}
