package br.edu.atitus.gatewayapi.security;

public record JwtClaims(String userId, String type, String email, String tokenId) {
}