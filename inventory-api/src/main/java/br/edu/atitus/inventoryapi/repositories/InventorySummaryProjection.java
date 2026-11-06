package br.edu.atitus.inventoryapi.repositories;

public interface InventorySummaryProjection {
    long getInventoryCount();

    long getQuantityOnHand();

    long getQuantityReserved();

    long getAvailableQuantity();

    long getOutOfStockCount();

    long getLowStockCount();
}
