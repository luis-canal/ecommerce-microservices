package br.edu.atitus.productapi.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        S3 s3,
        String publicBaseUrl
) {
    public record S3(
            String endpoint,
            String region,
            String accessKey,
            String secretKey,
            String bucket
    ) {
    }
}
