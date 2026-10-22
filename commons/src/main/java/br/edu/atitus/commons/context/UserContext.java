package br.edu.atitus.commons.context;

public record UserContext(Long userId, String type, String email) {

    public boolean isAdmin() {
        return "ADMINISTRADOR".equals(type);
    }
}