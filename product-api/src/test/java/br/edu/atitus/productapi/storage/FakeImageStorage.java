package br.edu.atitus.productapi.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Storage em memória para testes: nunca acessa o Supabase. */
public class FakeImageStorage implements ImageStorage {

    public record StoredObject(byte[] content, String contentType) {
    }

    public final Map<String, StoredObject> objects = new LinkedHashMap<>();
    public boolean failOnPut;
    public boolean failOnDelete;

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        if (failOnPut) {
            throw new IllegalStateException("falha simulada no put");
        }
        objects.put(objectKey, new StoredObject(content, contentType));
    }

    @Override
    public void delete(String objectKey) {
        if (failOnDelete) {
            throw new IllegalStateException("falha simulada no delete");
        }
        objects.remove(objectKey);
    }
}
