package br.edu.atitus.productapi.configs;

import br.edu.atitus.productapi.storage.ImageStorage;
import br.edu.atitus.productapi.storage.S3ImageStorage;
import br.edu.atitus.productapi.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public ImageStorage imageStorage(StorageProperties properties) {
        return new S3ImageStorage(properties.s3());
    }
}
