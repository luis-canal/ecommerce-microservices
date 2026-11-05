package br.edu.atitus.productapi.servicesimpl;

import br.edu.atitus.productapi.dtos.ProductRequest;
import br.edu.atitus.productapi.dtos.ProductResponse;
import br.edu.atitus.productapi.dtos.ProductStatusRequest;
import br.edu.atitus.productapi.dtos.ProductUpdateRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.enums.ProductType;
import br.edu.atitus.productapi.services.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductServiceMock implements ProductService {

    private final List<ProductEntity> database = new ArrayList<>();

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.promotion.message:Nenhuma Promoção Ativa}")
    private String promotionMessage;

    public ProductServiceMock() {
        this.database.add(mockProduct(1L, "iPhone 15 128GB", "Apple", "iPhone 15", 799.00));
        this.database.add(mockProduct(2L, "iPhone 15 Pro 256GB", "Apple", "iPhone 15 Pro", 1099.00));
        this.database.add(mockProduct(3L, "Galaxy S24 256GB", "Samsung", "Galaxy S24", 859.00));
        this.database.add(mockProduct(4L, "Galaxy S24 Ultra 512GB", "Samsung", "Galaxy S24 Ultra", 1299.00));
    }

    private ProductEntity mockProduct(Long id, String name, String brand, String model, double price) {
        var product = new ProductEntity();
        product.setId(id);
        product.setName(name);
        product.setBrand(brand);
        product.setModel(model);
        product.setDescription("Smartphone " + brand + " " + model);
        product.setCurrency("USD");
        product.setPrice(price);
        product.setImage("http://image.com/image.jpg");
        product.setProductType(ProductType.MULTI_UNIT);
        return product;
    }

    @Override
    public ProductEntity save(ProductRequest dto) throws Exception {
        return null;
    }

    @Override
    public ProductEntity update(Long id, ProductUpdateRequest request) {
        throw new UnsupportedOperationException("Edição não disponível no serviço mock");
    }

    @Override
    public ProductEntity updateStatus(Long id, ProductStatusRequest request) {
        throw new UnsupportedOperationException("Alteração de status não disponível no serviço mock");
    }

    @Override
    public ProductResponse findById(Long id, String targetcurrency) throws Exception {
        ProductEntity product = database.stream()
                .filter(item -> item.getId().equals(id))
                .filter(ProductEntity::isActive)
                .findFirst()
                .orElseThrow(() -> new Exception("Produto não encontrado"));

        String environment = "Product API running in port " + serverPort;

        return ProductResponse.fromEntity(product, environment, promotionMessage, targetcurrency, product.getPrice());
    }

    @Override
    public Page<ProductResponse> findAll(Pageable pageable, String targetCurrency) throws Exception {
        String environment = "Product API running in port " + serverPort;

        // 1. Mapeia a lista inteira do Mock para DTOs
        List<ProductResponse> dtoList = database.stream()
                .filter(ProductEntity::isActive)
                .map(product -> ProductResponse.fromEntity(
                        product,
                        environment,
                        promotionMessage,
                        targetCurrency,
                        product.getPrice()
                ))
                .toList();

        // 2. Calcula a fatia manual baseada no Pageable recebido
        int totalElements = dtoList.size();
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), totalElements);

        // Trata estouro de limites de páginas
        List<ProductResponse> pagedContent = (fromIndex > totalElements)
                ? List.of()
                : dtoList.subList(fromIndex, toIndex);

        // 3. Retorna a implementação oficial da interface Page do Spring Data
        return new PageImpl<>(pagedContent, pageable, totalElements);
    }}