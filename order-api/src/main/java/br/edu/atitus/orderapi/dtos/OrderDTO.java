package br.edu.atitus.orderapi.dtos;

import java.util.List;

public record OrderDTO(List<OrderItemDTO> items, ShippingAddressDTO shippingAddress) {
}
