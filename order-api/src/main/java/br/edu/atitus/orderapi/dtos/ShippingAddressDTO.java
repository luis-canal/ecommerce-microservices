package br.edu.atitus.orderapi.dtos;

public record ShippingAddressDTO(
        String zipCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state
) {
}
