package br.edu.atitus.inventoryapi.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryAuthorizationService {

    public void requireAdmin(Integer userType) {
        if (userType == null || userType != 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Usuário sem permissão para esta operação."
            );
        }
    }
}
