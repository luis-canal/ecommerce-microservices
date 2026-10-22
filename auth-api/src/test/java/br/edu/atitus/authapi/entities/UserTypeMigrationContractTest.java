package br.edu.atitus.authapi.entities;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserTypeMigrationContractTest {
    @Test
    void persistenceUsesStringValuesInsteadOfOrdinals() throws Exception {
        Field field = UserEntity.class.getDeclaredField("type");
        Enumerated enumerated = field.getAnnotation(Enumerated.class);

        assertNotNull(enumerated);
        assertEquals(EnumType.STRING, enumerated.value());
        assertEquals("ADMINISTRADOR", UserType.ADMINISTRADOR.name());
        assertEquals("CLIENTE", UserType.CLIENTE.name());
    }
}