package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.ProductImageResponse;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductWithImages;
import br.edu.atitus.productapi.entities.ProductImageEntity;
import br.edu.atitus.productapi.repositories.ProductImageRepository;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.storage.ImageStorage;
import br.edu.atitus.productapi.storage.ImageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ImageStorage storage;
    private final TransactionOperations transactions;
    private final String publicBaseUrl;
    private final long maxSizeBytes;
    private final int maxPerProduct;

    public ProductImageService(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            ImageStorage storage,
            TransactionOperations transactions,
            @Value("${app.storage.public-base-url:}") String publicBaseUrl,
            @Value("${app.images.max-size-bytes:5242880}") long maxSizeBytes,
            @Value("${app.images.max-per-product:8}") int maxPerProduct
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.transactions = transactions;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        this.maxSizeBytes = maxSizeBytes;
        this.maxPerProduct = maxPerProduct;
    }

    /**
     * Envia o arquivo ao storage e depois grava a linha no banco.
     * Se a gravação no banco falhar, o arquivo enviado é removido.
     */
    public ProductImageResponse upload(Long productId, MultipartFile file) {
        byte[] content = readValidatedContent(file);
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> badRequest("Formato de imagem não suportado. Envie JPEG, PNG ou WebP."));

        if (!productRepository.existsById(productId)) {
            throw productNotFound();
        }
        requireCapacity(productId);

        String objectKey = "products/" + productId + "/" + UUID.randomUUID() + "." + type.extension();
        try {
            storage.put(objectKey, content, type.contentType());
        } catch (RuntimeException exception) {
            log.error("Falha ao enviar a imagem {} ao storage", objectKey, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao enviar a imagem ao armazenamento.");
        }

        try {
            ProductImageEntity saved = transactions.execute(status -> {
                productRepository.lockById(productId).orElseThrow(this::productNotFound);
                long count = requireCapacity(productId);

                var image = new ProductImageEntity();
                image.setProductId(productId);
                image.setObjectKey(objectKey);
                image.setContentType(type.contentType());
                image.setSizeBytes(content.length);
                image.setPrimary(count == 0);
                image.setPosition(imageRepository.findMaxPosition(productId) + 1);
                return imageRepository.save(image);
            });
            return toResponse(saved);
        } catch (RuntimeException exception) {
            deleteFromStorageQuietly(objectKey);
            throw exception;
        }
    }

    public List<ProductImageResponse> list(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw productNotFound();
        }
        return imageRepository.findByProductIdOrderByPositionAscIdAsc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductImageResponse setPrimary(Long productId, Long imageId) {
        ProductImageEntity image = transactions.execute(status -> {
            productRepository.lockById(productId).orElseThrow(this::productNotFound);
            var target = imageRepository.findByIdAndProductId(imageId, productId)
                    .orElseThrow(this::imageNotFound);
            if (target.isPrimary()) {
                return target;
            }
            // Desmarca a principal atual antes de marcar a nova, respeitando o índice único parcial.
            imageRepository.findFirstByProductIdAndPrimaryTrue(productId).ifPresent(current -> {
                current.setPrimary(false);
                imageRepository.saveAndFlush(current);
            });
            target.setPrimary(true);
            return imageRepository.saveAndFlush(target);
        });
        return toResponse(image);
    }

    /**
     * Apaga a linha e, após o commit, o arquivo. Uma falha ao apagar o arquivo
     * é apenas registrada em log, sem quebrar a requisição.
     */
    public void delete(Long productId, Long imageId) {
        String objectKey = transactions.execute(status -> {
            productRepository.lockById(productId).orElseThrow(this::productNotFound);
            var image = imageRepository.findByIdAndProductId(imageId, productId)
                    .orElseThrow(this::imageNotFound);
            imageRepository.delete(image);
            imageRepository.flush();

            if (image.isPrimary()) {
                imageRepository.findByProductIdOrderByPositionAscIdAsc(productId).stream()
                        .findFirst()
                        .ifPresent(next -> {
                            next.setPrimary(true);
                            imageRepository.saveAndFlush(next);
                        });
            }
            return image.getObjectKey();
        });
        deleteFromStorageQuietly(objectKey);
    }

    /**
     * Carrega as imagens de vários produtos em uma única consulta (product_id IN ...).
     * Produtos sem imagens recebem lista vazia.
     */
    public Map<Long, List<ProductImageResponse>> findByProductIds(Collection<Long> productIds) {
        Map<Long, List<ProductImageResponse>> result = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        productIds.forEach(id -> result.put(id, new ArrayList<>()));
        for (var image : imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(productIds)) {
            result.computeIfAbsent(image.getProductId(), id -> new ArrayList<>()).add(toResponse(image));
        }
        result.replaceAll((id, images) -> List.copyOf(images));
        return result;
    }

    public List<ProductImageResponse> findByProductId(Long productId) {
        return findByProductIds(List.of(productId)).getOrDefault(productId, List.of());
    }

    /** URL da imagem principal, usada nos snapshots de carrinho e pedido. */
    public Optional<String> findPrimaryImageUrl(Long productId) {
        return imageRepository.findFirstByProductIdAndPrimaryTrue(productId)
                .map(image -> publicUrl(image.getObjectKey()));
    }

    /**
     * Produto com subcategoria e categoria já carregadas (join fetch) e suas imagens,
     * para as respostas administrativas sem depender de lazy loading.
     */
    public ProductWithImages findProductWithImages(Long productId) {
        return transactions.execute(status -> {
            var product = productRepository.findCartDataById(productId).orElseThrow(this::productNotFound);
            var images = findByProductId(productId);
            return new ProductWithImages(product, ProductResponse.resolveImage(product.getImage(), images), images);
        });
    }

    public String publicUrl(String objectKey) {
        return publicBaseUrl.isEmpty() ? objectKey : publicBaseUrl + "/" + objectKey;
    }

    private ProductImageResponse toResponse(ProductImageEntity image) {
        return new ProductImageResponse(
                image.getId(),
                publicUrl(image.getObjectKey()),
                image.isPrimary(),
                image.getPosition()
        );
    }

    private byte[] readValidatedContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Arquivo de imagem obrigatório no campo \"file\".");
        }
        if (file.getSize() > maxSizeBytes) {
            throw tooLarge();
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw badRequest("Não foi possível ler o arquivo enviado.");
        }
        if (content.length > maxSizeBytes) {
            throw tooLarge();
        }
        return content;
    }

    private long requireCapacity(Long productId) {
        long count = imageRepository.countByProductId(productId);
        if (count >= maxPerProduct) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Limite de " + maxPerProduct + " imagens por produto atingido."
            );
        }
        return count;
    }

    private void deleteFromStorageQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("Falha ao remover a imagem {} do storage; o arquivo ficou órfão", objectKey, exception);
        }
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "A imagem excede o tamanho máximo de " + (maxSizeBytes / (1024 * 1024)) + " MB."
        );
    }

    private ResponseStatusException productNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
    }

    private ResponseStatusException imageNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada.");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
