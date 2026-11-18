package br.edu.atitus.productapi.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Implementação compatível com a API S3 (Supabase Storage, Cloudflare R2, MinIO).
 * O cliente é criado sob demanda para que a aplicação suba sem credenciais configuradas.
 */
public class S3ImageStorage implements ImageStorage {

    private final StorageProperties.S3 properties;
    private volatile S3Client client;

    public S3ImageStorage(StorageProperties.S3 properties) {
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        client().putObject(
                PutObjectRequest.builder()
                        .bucket(bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content)
        );
    }

    @Override
    public void delete(String objectKey) {
        client().deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket())
                        .key(objectKey)
                        .build()
        );
    }

    private String bucket() {
        return require(properties == null ? null : properties.bucket(), "STORAGE_S3_BUCKET");
    }

    private S3Client client() {
        S3Client current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = buildClient();
                    client = current;
                }
            }
        }
        return current;
    }

    private S3Client buildClient() {
        if (properties == null) {
            throw new IllegalStateException("Armazenamento de imagens não configurado.");
        }
        return S3Client.builder()
                .endpointOverride(URI.create(require(properties.endpoint(), "STORAGE_S3_ENDPOINT")))
                .region(Region.of(require(properties.region(), "STORAGE_S3_REGION")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        require(properties.accessKey(), "STORAGE_S3_ACCESS_KEY"),
                        require(properties.secretKey(), "STORAGE_S3_SECRET_KEY")
                )))
                .forcePathStyle(true)
                .build();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Armazenamento de imagens não configurado: " + name + " ausente.");
        }
        return value.trim();
    }
}
