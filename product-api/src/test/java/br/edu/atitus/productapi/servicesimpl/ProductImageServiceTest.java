package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.ProductImageResponse;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.ProductImageEntity;
import br.edu.atitus.productapi.repositories.ProductImageRepository;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.storage.FakeImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductImageServiceTest {

    private static final long PRODUCT_ID = 1L;
    private static final long MAX_SIZE = 1024;
    private static final int MAX_PER_PRODUCT = 3;
    private static final String BASE_URL = "https://cdn.example.test/storage/v1/object/public/product-images/";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    private final List<ProductImageEntity> rows = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private ProductImageRepository imageRepository;
    private FakeImageStorage storage;
    private ProductImageService service;
    private boolean failOnSave;

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        when(productRepository.existsById(anyLong())).thenAnswer(inv -> inv.getArgument(0).equals(PRODUCT_ID));
        when(productRepository.lockById(anyLong())).thenAnswer(inv -> inv.getArgument(0).equals(PRODUCT_ID)
                ? Optional.of(new ProductEntity())
                : Optional.empty());

        imageRepository = inMemoryImageRepository();
        storage = new FakeImageStorage();
        service = new ProductImageService(
                productRepository,
                imageRepository,
                storage,
                TransactionOperations.withoutTransaction(),
                BASE_URL,
                MAX_SIZE,
                MAX_PER_PRODUCT
        );
    }

    @Test
    void uploadStoresDetectedContentTypeAndKeyPattern() {
        // A extensão e o Content-Type do cliente são ignorados: vale o conteúdo (PNG).
        var response = service.upload(PRODUCT_ID, file("foto.jpg", "image/jpeg", PNG));

        assertEquals(1, storage.objects.size());
        String key = storage.objects.keySet().iterator().next();
        assertTrue(key.matches("products/1/[0-9a-f\\-]{36}\\.png"), key);
        assertEquals("image/png", storage.objects.get(key).contentType());
        assertEquals("https://cdn.example.test/storage/v1/object/public/product-images/" + key, response.url());
        assertEquals("image/png", rows.getFirst().getContentType());
        assertEquals(PNG.length, rows.getFirst().getSizeBytes());
    }

    @Test
    void acceptsJpegAndWebp() {
        service.upload(PRODUCT_ID, file("a.bin", "application/octet-stream", JPEG));
        service.upload(PRODUCT_ID, file("b.bin", "application/octet-stream", WEBP));

        assertEquals(List.of("image/jpeg", "image/webp"),
                storage.objects.values().stream().map(FakeImageStorage.StoredObject::contentType).toList());
    }

    @Test
    void rejectsContentThatIsNotARealImage() {
        var exception = assertThrows(ResponseStatusException.class, () ->
                service.upload(PRODUCT_ID, file("falsa.png", "image/png", "não sou imagem".getBytes())));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(storage.objects.isEmpty());
        assertTrue(rows.isEmpty());
    }

    @Test
    void rejectsEmptyFile() {
        var exception = assertThrows(ResponseStatusException.class, () ->
                service.upload(PRODUCT_ID, file("vazia.png", "image/png", new byte[0])));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsImagesAboveTheSizeLimit() {
        byte[] large = new byte[(int) MAX_SIZE + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);

        var exception = assertThrows(ResponseStatusException.class, () ->
                service.upload(PRODUCT_ID, file("grande.png", "image/png", large)));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void rejectsUploadAboveThePerProductLimit() {
        for (int i = 0; i < MAX_PER_PRODUCT; i++) {
            service.upload(PRODUCT_ID, file("img.png", "image/png", PNG));
        }

        var exception = assertThrows(ResponseStatusException.class, () ->
                service.upload(PRODUCT_ID, file("extra.png", "image/png", PNG)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(MAX_PER_PRODUCT, rows.size());
        assertEquals(MAX_PER_PRODUCT, storage.objects.size());
    }

    @Test
    void unknownProductReturns404WithoutTouchingStorage() {
        var upload = assertThrows(ResponseStatusException.class, () ->
                service.upload(99L, file("img.png", "image/png", PNG)));
        var list = assertThrows(ResponseStatusException.class, () -> service.list(99L));
        var primary = assertThrows(ResponseStatusException.class, () -> service.setPrimary(99L, 1L));
        var delete = assertThrows(ResponseStatusException.class, () -> service.delete(99L, 1L));

        assertEquals(HttpStatus.NOT_FOUND, upload.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, list.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, primary.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void firstUploadBecomesPrimaryAndPositionsIncrease() {
        var first = service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));
        var second = service.upload(PRODUCT_ID, file("2.png", "image/png", PNG));

        assertTrue(first.isPrimary());
        assertFalse(second.isPrimary());
        assertEquals(1, first.position());
        assertEquals(2, second.position());
    }

    @Test
    void setPrimarySwapsThePrimaryImage() {
        var first = service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));
        var second = service.upload(PRODUCT_ID, file("2.png", "image/png", PNG));

        var updated = service.setPrimary(PRODUCT_ID, second.id());

        assertTrue(updated.isPrimary());
        List<ProductImageResponse> images = service.list(PRODUCT_ID);
        assertEquals(List.of(false, true), images.stream().map(ProductImageResponse::isPrimary).toList());
        assertEquals(first.id(), images.getFirst().id());
    }

    @Test
    void setPrimaryOnImageOfAnotherProductReturns404() {
        var image = service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));
        rows.getFirst().setProductId(2L);

        var exception = assertThrows(ResponseStatusException.class, () -> service.setPrimary(PRODUCT_ID, image.id()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void removingThePrimaryPromotesTheNextByPosition() {
        var first = service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));
        var second = service.upload(PRODUCT_ID, file("2.png", "image/png", PNG));
        var third = service.upload(PRODUCT_ID, file("3.png", "image/png", PNG));

        service.delete(PRODUCT_ID, first.id());

        var images = service.list(PRODUCT_ID);
        assertEquals(List.of(second.id(), third.id()), images.stream().map(ProductImageResponse::id).toList());
        assertTrue(images.get(0).isPrimary());
        assertFalse(images.get(1).isPrimary());
        assertEquals(2, storage.objects.size());
    }

    @Test
    void removingANonPrimaryKeepsThePrimary() {
        var first = service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));
        var second = service.upload(PRODUCT_ID, file("2.png", "image/png", PNG));

        service.delete(PRODUCT_ID, second.id());

        var images = service.list(PRODUCT_ID);
        assertEquals(1, images.size());
        assertEquals(first.id(), images.getFirst().id());
        assertTrue(images.getFirst().isPrimary());
    }

    @Test
    void storageFailureOnDeleteDoesNotBreakTheRequest() {
        var image = service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));
        storage.failOnDelete = true;

        service.delete(PRODUCT_ID, image.id());

        assertTrue(rows.isEmpty());
    }

    @Test
    void databaseFailureRemovesTheUploadedFile() {
        failOnSave = true;

        assertThrows(DataIntegrityViolationException.class, () ->
                service.upload(PRODUCT_ID, file("1.png", "image/png", PNG)));

        assertTrue(storage.objects.isEmpty());
        assertTrue(rows.isEmpty());
    }

    @Test
    void storageFailureOnUploadReturns502AndSavesNothing() {
        storage.failOnPut = true;

        var exception = assertThrows(ResponseStatusException.class, () ->
                service.upload(PRODUCT_ID, file("1.png", "image/png", PNG)));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertTrue(rows.isEmpty());
    }

    @Test
    void loadsImagesOfManyProductsWithEmptyListForProductsWithoutImages() {
        service.upload(PRODUCT_ID, file("1.png", "image/png", PNG));

        var result = service.findByProductIds(List.of(PRODUCT_ID, 2L));

        assertEquals(1, result.get(PRODUCT_ID).size());
        assertEquals(List.of(), result.get(2L));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    /**
     * Repositório em memória que também simula o índice único parcial
     * (no máximo uma imagem principal por produto).
     */
    private ProductImageRepository inMemoryImageRepository() {
        ProductImageRepository repository = mock(ProductImageRepository.class);

        org.mockito.stubbing.Answer<ProductImageEntity> save = inv -> {
            ProductImageEntity image = inv.getArgument(0);
            if (failOnSave) {
                throw new DataIntegrityViolationException("falha simulada no banco");
            }
            if (image.getId() == null) {
                image.setId(sequence.incrementAndGet());
                rows.add(image);
            }
            long primaries = rows.stream()
                    .filter(row -> row.getProductId().equals(image.getProductId()) && row.isPrimary())
                    .count();
            if (primaries > 1) {
                throw new DataIntegrityViolationException("uk_product_images_primary");
            }
            return image;
        };
        when(repository.save(any())).thenAnswer(save);
        when(repository.saveAndFlush(any())).thenAnswer(save);
        doAnswer(inv -> rows.remove(inv.<ProductImageEntity>getArgument(0))).when(repository).delete(any());

        when(repository.findByProductIdOrderByPositionAscIdAsc(anyLong()))
                .thenAnswer(inv -> sorted(rows.stream().filter(row -> row.getProductId().equals(inv.getArgument(0))).toList()));
        when(repository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(anyCollection()))
                .thenAnswer(inv -> {
                    Collection<Long> ids = inv.getArgument(0);
                    return sorted(rows.stream().filter(row -> ids.contains(row.getProductId())).toList());
                });
        when(repository.findByIdAndProductId(anyLong(), anyLong()))
                .thenAnswer(inv -> rows.stream()
                        .filter(row -> row.getId().equals(inv.getArgument(0))
                                && row.getProductId().equals(inv.getArgument(1)))
                        .findFirst());
        when(repository.findFirstByProductIdAndPrimaryTrue(anyLong()))
                .thenAnswer(inv -> rows.stream()
                        .filter(row -> row.getProductId().equals(inv.getArgument(0)) && row.isPrimary())
                        .findFirst());
        when(repository.countByProductId(anyLong()))
                .thenAnswer(inv -> rows.stream().filter(row -> row.getProductId().equals(inv.getArgument(0))).count());
        when(repository.findMaxPosition(anyLong()))
                .thenAnswer(inv -> rows.stream()
                        .filter(row -> row.getProductId().equals(inv.getArgument(0)))
                        .mapToInt(ProductImageEntity::getPosition)
                        .max()
                        .orElse(0));
        return repository;
    }

    private static List<ProductImageEntity> sorted(List<ProductImageEntity> images) {
        return images.stream()
                .sorted(Comparator.comparing(ProductImageEntity::getProductId)
                        .thenComparingInt(ProductImageEntity::getPosition)
                        .thenComparing(ProductImageEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
