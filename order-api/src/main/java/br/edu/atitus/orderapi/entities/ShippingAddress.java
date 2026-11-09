package br.edu.atitus.orderapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ShippingAddress {

    @Column(name = "shipping_zip_code", length = 9)
    private String zipCode;

    @Column(name = "shipping_street", length = 200)
    private String street;

    @Column(name = "shipping_number", length = 32)
    private String number;

    @Column(name = "shipping_complement", length = 200)
    private String complement;

    @Column(name = "shipping_neighborhood", length = 120)
    private String neighborhood;

    @Column(name = "shipping_city", length = 120)
    private String city;

    @Column(name = "shipping_state", length = 2)
    private String state;

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getComplement() {
        return complement;
    }

    public void setComplement(String complement) {
        this.complement = complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
