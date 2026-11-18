package br.edu.atitus.productapi.storage;

/**
 * Abstração do armazenamento de imagens de produto.
 * Permite trocar de provedor (Supabase, R2, MinIO) apenas por configuração.
 */
public interface ImageStorage {

    void put(String objectKey, byte[] content, String contentType);

    void delete(String objectKey);
}
